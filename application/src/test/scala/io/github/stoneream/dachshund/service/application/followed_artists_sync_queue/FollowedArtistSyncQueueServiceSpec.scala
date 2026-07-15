package io.github.stoneream.dachshund.service.application.followed_artists_sync_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.FollowedArtistSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{FollowedArtistSyncQueueSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.FollowedArtistSyncQueueDbRow
import io.github.stoneream.dachshund.infra.db.reader.followed_artists_sync_queue.FollowedArtistSyncQueueReader
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{FollowedArtistSyncQueueWriter, SpotifyUserWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.FollowedArtistSyncQueueProgressResult as ProgressResult
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.FollowedArtistSyncQueueUpdateResult.Updated
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget
import io.github.stoneream.dachshund.test.lib.db.DatabaseSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.duration.*

class FollowedArtistSyncQueueServiceSpec extends AnyFeatureSpec with ScalaFutures with DatabaseSupport {
  private val userWriter = new SpotifyUserWriter
  private val queueWriter = new FollowedArtistSyncQueueWriter
  private val service = new FollowedArtistSyncQueueServiceImpl(
    databaseTransaction = databaseTransaction,
    queueReader = new FollowedArtistSyncQueueReader,
    queueWriter = queueWriter,
    databaseExecutor = databaseExecutor
  )

  Feature("Followed artist sync queue service") {
    Scenario("claim 前に stale queue を復旧して lease 付き target を返す") {
      val (scheduledUserId, staleUserId) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val scheduledUserId = userWriter.write(Rows.ScheduledUserRow)
        queueWriter.write(Rows.scheduledQueueRow(scheduledUserId))

        val staleUserId = userWriter.write(Rows.StaleUserRow)
        queueWriter.write(Rows.staleQueueRow(staleUserId))
        (scheduledUserId, staleUserId)
      }

      val result = service.claimDueTargets(fixedNow, batchSize = 25, processingLease = 30.minutes).futureValue

      assert(result.size == 2)
      val scheduledClaimed = result.find(_.userId == scheduledUserId).get
      assert(scheduledClaimed.status == QueueJobStatus.Processing)
      assert(scheduledClaimed.attemptCount == 1)
      assert(scheduledClaimed.lockToken.nonEmpty)
      assert(scheduledClaimed.queueLockVersion == 1L)
      assert(scheduledClaimed.lastAttemptedAt.map(_.toLocalDateTime) == Some(fixedNow.toLocalDateTime))
      assert(scheduledClaimed.lockedUntil.map(_.toLocalDateTime) == Some(fixedNow.plus(30.minutes).toLocalDateTime))

      val staleClaimed = result.find(_.userId == staleUserId).get
      assert(staleClaimed.status == QueueJobStatus.Processing)
      assert(staleClaimed.attemptCount == 2)
      assert(staleClaimed.lockToken.nonEmpty)
      assert(staleClaimed.queueLockVersion == 4L)
      assert(staleClaimed.lastAttemptedAt.map(_.toLocalDateTime) == Some(fixedNow.toLocalDateTime))
      assert(staleClaimed.lockedUntil.map(_.toLocalDateTime) == Some(fixedNow.plus(30.minutes).toLocalDateTime))

      val rows = followedQueueRows()
      val scheduledRow = rows.find(_.userId == scheduledUserId).get
      val staleRow = rows.find(_.userId == staleUserId).get
      assert(scheduledRow.status == QueueJobStatus.Processing.dbValue)
      assert(scheduledRow.attemptCount == 1)
      assert(scheduledRow.lockToken.nonEmpty)
      assert(scheduledRow.lockedUntil == Some(fixedNow.plus(30.minutes).toLocalDateTime))
      assert(scheduledRow.lockVersion == 1L)
      assert(staleRow.status == QueueJobStatus.Processing.dbValue)
      assert(staleRow.attemptCount == 2)
      assert(staleRow.lockToken.nonEmpty)
      assert(staleRow.lockedUntil == Some(fixedNow.plus(30.minutes).toLocalDateTime))
      assert(staleRow.lockVersion == 4L)
    }

    Scenario("中間ページの処理結果は processing のまま cursor と lock version を進める") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.ProgressUserRow)
        queueWriter.write(Rows.progressQueueRow(userId))
        userId
      }
      val target = findFollowedQueueTarget(userId, fixedNow.toLocalDate)

      val result = service.markPageProgressed(target, nextAfterCursor = "next-cursor", fixedNow).futureValue

      result match {
        case ProgressResult.Updated(updatedTarget) =>
          assert(updatedTarget.queueId == target.queueId)
          assert(updatedTarget.afterCursor == Some("next-cursor"))
          assert(updatedTarget.queueLockVersion == 3L)
          assert(updatedTarget.status == QueueJobStatus.Processing)
        case ProgressResult.StaleLockSkipped => fail("expected updated result")
      }
      val row = followedQueueRows().find(_.userId == userId).get
      assert(row.status == QueueJobStatus.Processing.dbValue)
      assert(row.afterCursor == Some("next-cursor"))
      assert(row.lockToken == "lock-token")
      assert(row.lockVersion == 3L)
    }

    Scenario("ページ処理結果を scheduled または succeeded として保存する") {
      val (nextPageUserId, completedUserId) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val nextPageUserId = userWriter.write(Rows.NextPageUserRow)
        queueWriter.write(Rows.nextPageQueueRow(nextPageUserId))

        val completedUserId = userWriter.write(Rows.CompletedUserRow)
        queueWriter.write(Rows.completedQueueRow(completedUserId))
        (nextPageUserId, completedUserId)
      }

      val nextPageTarget = findFollowedQueueTarget(nextPageUserId, fixedNow.toLocalDate)
      val completedTarget = findFollowedQueueTarget(completedUserId, fixedNow.toLocalDate)

      val nextPageResult =
        service.markPageProcessed(nextPageTarget, nextAfterCursor = Some("next-cursor"), fixedNow).futureValue
      val completedResult = service.markPageProcessed(completedTarget, nextAfterCursor = None, fixedNow).futureValue

      assert(nextPageResult == Updated)
      assert(completedResult == Updated)
      val rows = followedQueueRows()
      val nextPageRow = rows.find(_.userId == nextPageUserId).get
      val completedRow = rows.find(_.userId == completedUserId).get
      assert(nextPageRow.status == QueueJobStatus.Scheduled.dbValue)
      assert(nextPageRow.afterCursor == Some("next-cursor"))
      assert(nextPageRow.nextAttemptAt == Some(fixedNow.toLocalDateTime))
      assert(nextPageRow.completedAt.isEmpty)
      assert(nextPageRow.attemptCount == 0)
      assert(nextPageRow.lockToken == "")
      assert(nextPageRow.lockVersion == 5L)
      assert(completedRow.status == QueueJobStatus.Succeeded.dbValue)
      assert(completedRow.afterCursor.isEmpty)
      assert(completedRow.nextAttemptAt.isEmpty)
      assert(completedRow.completedAt == Some(fixedNow.toLocalDateTime))
      assert(completedRow.attemptCount == 0)
      assert(completedRow.lockToken == "")
      assert(completedRow.lockVersion == 6L)
    }
  }

  private final case class FollowedQueueRow(
      userId: Long,
      status: String,
      afterCursor: Option[String],
      nextAttemptAt: Option[LocalDateTime],
      completedAt: Option[LocalDateTime],
      attemptCount: Int,
      lockToken: String,
      lockedUntil: Option[LocalDateTime],
      lockVersion: Long
  )

  private def findFollowedQueueTarget(userId: Long, syncDate: LocalDate): FollowedArtistSyncQueueTarget =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          id,
          user_id,
          sync_date,
          status,
          requested_limit,
          after_cursor,
          next_attempt_at,
          last_attempted_at,
          completed_at,
          attempt_count,
          last_failed_at,
          last_error_type,
          lock_token,
          locked_until,
          deleted_at,
          deleted,
          lock_version
        from followed_artist_sync_queue
        where user_id = {userId}
          and sync_date = {syncDate}
      """
        .bindByName("userId" -> userId, "syncDate" -> syncDate)
        .map { rs =>
          FollowedArtistSyncQueueTarget(
            queueId = rs.long("id"),
            userId = rs.long("user_id"),
            syncDate = rs.localDate("sync_date"),
            requestedLimit = rs.int("requested_limit"),
            afterCursor = rs.stringOpt("after_cursor"),
            attemptCount = rs.int("attempt_count"),
            lockToken = rs.string("lock_token"),
            queueLockVersion = rs.long("lock_version"),
            status = QueueJobStatus.fromDbValue(rs.string("status")),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
            lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
            completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
            lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
            lastErrorType = rs.string("last_error_type"),
            lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
            deletedAt = rs.localDateTimeOpt("deleted_at").map(BusinessDateTime.fromLocalDateTime),
            deletedUser = AuditUser.Empty,
            deleted = rs.long("deleted")
          )
        }
        .single
        .apply()
        .get
    }

  private def followedQueueRows(): Seq[FollowedQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          user_id,
          status,
          after_cursor,
          next_attempt_at,
          completed_at,
          attempt_count,
          lock_token,
          locked_until,
          lock_version
        from followed_artist_sync_queue
        order by id asc
      """
        .map { rs =>
          FollowedQueueRow(
            userId = rs.long("user_id"),
            status = rs.string("status"),
            afterCursor = rs.stringOpt("after_cursor"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at"),
            completedAt = rs.localDateTimeOpt("completed_at"),
            attemptCount = rs.int("attempt_count"),
            lockToken = rs.string("lock_token"),
            lockedUntil = rs.localDateTimeOpt("locked_until"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  private object Rows {
    val ScheduledUserRow = UserSource(
      userName = "scheduled-user",
      displayName = "scheduled user",
      timeZone = "Asia/Tokyo",
      enabled = 1L,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserDbRow

    val StaleUserRow = UserSource(
      userName = "stale-user",
      displayName = "stale user",
      timeZone = "Asia/Tokyo",
      enabled = 1L,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserDbRow

    val ProgressUserRow = UserSource(
      userName = "progress-user",
      displayName = "progress user",
      timeZone = "Asia/Tokyo",
      enabled = 1L,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserDbRow

    val NextPageUserRow = UserSource(
      userName = "next-page-user",
      displayName = "next page user",
      timeZone = "Asia/Tokyo",
      enabled = 1L,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserDbRow

    val CompletedUserRow = UserSource(
      userName = "completed-user",
      displayName = "completed user",
      timeZone = "Asia/Tokyo",
      enabled = 1L,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserDbRow

    def scheduledQueueRow(userId: Long): FollowedArtistSyncQueueDbRow = FollowedArtistSyncQueueSource(
      userId = userId,
      syncDate = fixedNow.toLocalDate,
      status = QueueJobStatus.Scheduled,
      requestedLimit = 50,
      afterCursor = None,
      nextAttemptAt = Some(fixedNow),
      lastAttemptedAt = None,
      completedAt = None,
      attemptCount = 0,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "",
      lockedUntil = None,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toFollowedArtistSyncQueueDbRow

    def staleQueueRow(userId: Long): FollowedArtistSyncQueueDbRow = FollowedArtistSyncQueueSource(
      userId = userId,
      syncDate = fixedNow.toLocalDate,
      status = QueueJobStatus.Processing,
      requestedLimit = 50,
      afterCursor = None,
      nextAttemptAt = Some(fixedNow.minus(10.minutes)),
      lastAttemptedAt = Some(fixedNow.minus(5.minutes)),
      completedAt = None,
      attemptCount = 1,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "stale-lock-token",
      lockedUntil = Some(fixedNow.minus(1.minute)),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 2L
    ).toFollowedArtistSyncQueueDbRow

    def progressQueueRow(userId: Long): FollowedArtistSyncQueueDbRow = FollowedArtistSyncQueueSource(
      userId = userId,
      syncDate = fixedNow.toLocalDate,
      status = QueueJobStatus.Processing,
      requestedLimit = 50,
      afterCursor = None,
      nextAttemptAt = Some(fixedNow),
      lastAttemptedAt = Some(fixedNow),
      completedAt = None,
      attemptCount = 1,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "lock-token",
      lockedUntil = Some(fixedNow.plus(30.minutes)),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 2L
    ).toFollowedArtistSyncQueueDbRow

    def nextPageQueueRow(userId: Long): FollowedArtistSyncQueueDbRow = FollowedArtistSyncQueueSource(
      userId = userId,
      syncDate = fixedNow.toLocalDate,
      status = QueueJobStatus.Processing,
      requestedLimit = 50,
      afterCursor = None,
      nextAttemptAt = Some(fixedNow.minus(1.minute)),
      lastAttemptedAt = Some(fixedNow),
      completedAt = None,
      attemptCount = 2,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "next-page-lock-token",
      lockedUntil = Some(fixedNow.plus(30.minutes)),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 4L
    ).toFollowedArtistSyncQueueDbRow

    def completedQueueRow(userId: Long): FollowedArtistSyncQueueDbRow = FollowedArtistSyncQueueSource(
      userId = userId,
      syncDate = fixedNow.toLocalDate,
      status = QueueJobStatus.Processing,
      requestedLimit = 50,
      afterCursor = None,
      nextAttemptAt = Some(fixedNow.minus(1.minute)),
      lastAttemptedAt = Some(fixedNow),
      completedAt = None,
      attemptCount = 2,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "completed-lock-token",
      lockedUntil = Some(fixedNow.plus(30.minutes)),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 5L
    ).toFollowedArtistSyncQueueDbRow
  }
}
