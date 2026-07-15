package io.github.stoneream.dachshund.service.application.artist_release_sync_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSyncQueueSource, UserFollowedArtistSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.UserFollowedArtistDbRow
import io.github.stoneream.dachshund.infra.db.reader.artist_release_sync_queue.ArtistReleaseSyncQueueReader
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleaseSyncQueueWriter, SpotifyUserWriter, UserFollowedArtistsWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.ArtistReleaseSyncQueueUpdateResult.Updated
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget
import io.github.stoneream.dachshund.test.lib.db.DatabaseSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.LocalDateTime
import scala.concurrent.duration.*

class ArtistReleaseSyncQueueServiceSpec extends AnyFeatureSpec with ScalaFutures with DatabaseSupport {
  private val userWriter = new SpotifyUserWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter
  private val queueWriter = new ArtistReleaseSyncQueueWriter
  private val service = new ArtistReleaseSyncQueueServiceImpl(
    databaseTransaction = databaseTransaction,
    queueReader = new ArtistReleaseSyncQueueReader,
    queueWriter = queueWriter,
    databaseExecutor = databaseExecutor
  )

  Feature("Artist release sync queue service") {
    Scenario("claim 前に stale queue を復旧して lease 付き target を返す") {
      val (scheduledArtistCode, staleArtistCode) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.ClaimUserRow)

        followedArtistWriter.write(Rows.scheduledArtistRow(userId))
        queueWriter.write(Rows.scheduledQueueRow)

        followedArtistWriter.write(Rows.staleArtistRow(userId))
        queueWriter.write(Rows.staleQueueRow)
        (Rows.ScheduledArtistCode, Rows.StaleArtistCode)
      }

      val result = service.claimDueTargets(fixedNow, batchSize = 25, processingLease = 30.minutes).futureValue

      assert(result.size == 2)
      val scheduledClaimed = result.find(_.spotifyArtistCode == scheduledArtistCode).get
      assert(scheduledClaimed.status == QueueJobStatus.Processing)
      assert(scheduledClaimed.attemptCount == 1)
      assert(scheduledClaimed.lockToken.nonEmpty)
      assert(scheduledClaimed.queueLockVersion == 1L)
      assert(scheduledClaimed.lastAttemptedAt.map(_.toLocalDateTime) == Some(fixedNow.toLocalDateTime))
      assert(scheduledClaimed.lockedUntil.map(_.toLocalDateTime) == Some(fixedNow.plus(30.minutes).toLocalDateTime))

      val staleClaimed = result.find(_.spotifyArtistCode == staleArtistCode).get
      assert(staleClaimed.status == QueueJobStatus.Processing)
      assert(staleClaimed.attemptCount == 2)
      assert(staleClaimed.lockToken.nonEmpty)
      assert(staleClaimed.queueLockVersion == 4L)
      assert(staleClaimed.lastAttemptedAt.map(_.toLocalDateTime) == Some(fixedNow.toLocalDateTime))
      assert(staleClaimed.lockedUntil.map(_.toLocalDateTime) == Some(fixedNow.plus(30.minutes).toLocalDateTime))

      val rows = artistReleaseQueueRows()
      val scheduledRow = rows.find(_.spotifyArtistCode == scheduledArtistCode).get
      val staleRow = rows.find(_.spotifyArtistCode == staleArtistCode).get
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

    Scenario("ページ処理結果を scheduled または succeeded として保存する") {
      val (nextPageArtistCode, completedArtistCode) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        queueWriter.write(Rows.nextPageQueueRow)
        queueWriter.write(Rows.completedQueueRow)
        (Rows.NextPageArtistCode, Rows.CompletedArtistCode)
      }

      val nextPageTarget = findArtistReleaseQueueTarget(nextPageArtistCode)
      val completedTarget = findArtistReleaseQueueTarget(completedArtistCode)

      val nextPageResult =
        service.markPageProcessed(nextPageTarget, nextOffset = 10, completed = false, now = fixedNow).futureValue
      val completedResult =
        service.markPageProcessed(completedTarget, nextOffset = 20, completed = true, now = fixedNow).futureValue

      assert(nextPageResult == Updated)
      assert(completedResult == Updated)
      val rows = artistReleaseQueueRows()
      val nextPageRow = rows.find(_.spotifyArtistCode == nextPageArtistCode).get
      val completedRow = rows.find(_.spotifyArtistCode == completedArtistCode).get
      assert(nextPageRow.status == QueueJobStatus.Scheduled.dbValue)
      assert(nextPageRow.nextOffset == 10)
      assert(nextPageRow.nextAttemptAt == Some(fixedNow.toLocalDateTime))
      assert(nextPageRow.completedAt.isEmpty)
      assert(nextPageRow.attemptCount == 0)
      assert(nextPageRow.lockToken == "")
      assert(nextPageRow.lockVersion == 5L)
      assert(completedRow.status == QueueJobStatus.Succeeded.dbValue)
      assert(completedRow.nextOffset == 20)
      assert(completedRow.nextAttemptAt.isEmpty)
      assert(completedRow.completedAt == Some(fixedNow.toLocalDateTime))
      assert(completedRow.attemptCount == 0)
      assert(completedRow.lockToken == "")
      assert(completedRow.lockVersion == 6L)
    }

    Scenario("一時失敗と blocked を保存する") {
      val (temporaryFailureArtistCode, blockedArtistCode) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        queueWriter.write(Rows.temporaryFailureQueueRow)
        queueWriter.write(Rows.blockedQueueRow)
        (Rows.TemporaryFailureArtistCode, Rows.BlockedArtistCode)
      }
      val temporaryFailureTarget = findArtistReleaseQueueTarget(temporaryFailureArtistCode)
      val blockedTarget = findArtistReleaseQueueTarget(blockedArtistCode)
      val nextAttemptAt = fixedNow.plus(10.minutes)

      val temporaryFailureResult =
        service.markTemporaryFailure(temporaryFailureTarget, "rate_limited", nextAttemptAt, fixedNow).futureValue
      val blockedResult = service.markBlocked(blockedTarget, "insufficient_scope", fixedNow).futureValue

      assert(temporaryFailureResult == Updated)
      assert(blockedResult == Updated)
      val rows = artistReleaseQueueRows()
      val temporaryFailureRow = rows.find(_.spotifyArtistCode == temporaryFailureArtistCode).get
      val blockedRow = rows.find(_.spotifyArtistCode == blockedArtistCode).get
      assert(temporaryFailureRow.status == QueueJobStatus.Scheduled.dbValue)
      assert(temporaryFailureRow.nextAttemptAt == Some(nextAttemptAt.toLocalDateTime))
      assert(temporaryFailureRow.lastFailedAt == Some(fixedNow.toLocalDateTime))
      assert(temporaryFailureRow.lastErrorType == "rate_limited")
      assert(temporaryFailureRow.lockToken == "")
      assert(temporaryFailureRow.lockVersion == 7L)
      assert(blockedRow.status == QueueJobStatus.Blocked.dbValue)
      assert(blockedRow.nextAttemptAt.isEmpty)
      assert(blockedRow.lastFailedAt == Some(fixedNow.toLocalDateTime))
      assert(blockedRow.lastErrorType == "insufficient_scope")
      assert(blockedRow.lockToken == "")
      assert(blockedRow.lockVersion == 8L)
    }
  }

  private final case class ArtistReleaseQueueRow(
      spotifyArtistCode: String,
      status: String,
      nextOffset: Int,
      nextAttemptAt: Option[LocalDateTime],
      completedAt: Option[LocalDateTime],
      attemptCount: Int,
      lastFailedAt: Option[LocalDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[LocalDateTime],
      lockVersion: Long
  )

  private def findArtistReleaseQueueTarget(spotifyArtistCode: String): ArtistReleaseSyncQueueTarget =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          id,
          spotify_artist_code,
          sync_scope,
          status,
          include_groups,
          market,
          requested_limit,
          next_offset,
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
        from artist_release_sync_queue
        where spotify_artist_code = {spotifyArtistCode}
      """
        .bindByName("spotifyArtistCode" -> spotifyArtistCode)
        .map { rs =>
          ArtistReleaseSyncQueueTarget(
            queueId = rs.long("id"),
            spotifyArtistCode = rs.string("spotify_artist_code"),
            syncScope = rs.string("sync_scope"),
            includeGroups = rs.string("include_groups"),
            market = rs.stringOpt("market"),
            requestedLimit = rs.int("requested_limit"),
            nextOffset = rs.int("next_offset"),
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

  private def artistReleaseQueueRows(): Seq[ArtistReleaseQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          spotify_artist_code,
          status,
          next_offset,
          next_attempt_at,
          completed_at,
          attempt_count,
          last_failed_at,
          last_error_type,
          lock_token,
          locked_until,
          lock_version
        from artist_release_sync_queue
        order by id asc
      """
        .map { rs =>
          ArtistReleaseQueueRow(
            spotifyArtistCode = rs.string("spotify_artist_code"),
            status = rs.string("status"),
            nextOffset = rs.int("next_offset"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at"),
            completedAt = rs.localDateTimeOpt("completed_at"),
            attemptCount = rs.int("attempt_count"),
            lastFailedAt = rs.localDateTimeOpt("last_failed_at"),
            lastErrorType = rs.string("last_error_type"),
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
  private val SyncScopeIncremental: String = "INCREMENTAL"
  private val IncludeGroupsAlbumSingle: String = "album,single"

  private object Rows {
    val ScheduledArtistCode = "scheduled-artist-code"
    val StaleArtistCode = "stale-artist-code"
    val NextPageArtistCode = "next-page-artist-code"
    val CompletedArtistCode = "completed-artist-code"
    val TemporaryFailureArtistCode = "temporary-failure-artist-code"
    val BlockedArtistCode = "blocked-artist-code"

    val ClaimUserRow = UserSource(
      userName = "claim-user",
      displayName = "claim user",
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

    def scheduledArtistRow(userId: Long): UserFollowedArtistDbRow =
      UserFollowedArtistSource(
        userId = userId,
        spotifyArtistCode = ScheduledArtistCode,
        artistName = "Scheduled Artist",
        spotifyArtistUri = "",
        spotifyUrl = "",
        href = "",
        primaryImageUrl = "",
        primaryImageHeight = None,
        primaryImageWidth = None,
        imagesJson = None,
        genresJson = None,
        followersTotal = None,
        popularity = None,
        firstFollowedAt = Some(fixedNow),
        lastSeenAt = Some(fixedNow),
        lastSyncedAt = Some(fixedNow),
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserFollowedArtistDbRow

    def staleArtistRow(userId: Long): UserFollowedArtistDbRow =
      UserFollowedArtistSource(
        userId = userId,
        spotifyArtistCode = StaleArtistCode,
        artistName = "Stale Artist",
        spotifyArtistUri = "",
        spotifyUrl = "",
        href = "",
        primaryImageUrl = "",
        primaryImageHeight = None,
        primaryImageWidth = None,
        imagesJson = None,
        genresJson = None,
        followersTotal = None,
        popularity = None,
        firstFollowedAt = Some(fixedNow),
        lastSeenAt = Some(fixedNow),
        lastSyncedAt = Some(fixedNow),
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserFollowedArtistDbRow

    val scheduledQueueRow = ArtistReleaseSyncQueueSource(
      spotifyArtistCode = ScheduledArtistCode,
      syncScope = SyncScopeIncremental,
      status = QueueJobStatus.Scheduled,
      includeGroups = IncludeGroupsAlbumSingle,
      market = None,
      requestedLimit = 10,
      nextOffset = 0,
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
    ).toArtistReleaseSyncQueueDbRow

    val staleQueueRow = ArtistReleaseSyncQueueSource(
      spotifyArtistCode = StaleArtistCode,
      syncScope = SyncScopeIncremental,
      status = QueueJobStatus.Processing,
      includeGroups = IncludeGroupsAlbumSingle,
      market = None,
      requestedLimit = 10,
      nextOffset = 0,
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
    ).toArtistReleaseSyncQueueDbRow

    val nextPageQueueRow = ArtistReleaseSyncQueueSource(
      spotifyArtistCode = NextPageArtistCode,
      syncScope = SyncScopeIncremental,
      status = QueueJobStatus.Processing,
      includeGroups = IncludeGroupsAlbumSingle,
      market = None,
      requestedLimit = 10,
      nextOffset = 5,
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
    ).toArtistReleaseSyncQueueDbRow

    val completedQueueRow = ArtistReleaseSyncQueueSource(
      spotifyArtistCode = CompletedArtistCode,
      syncScope = SyncScopeIncremental,
      status = QueueJobStatus.Processing,
      includeGroups = IncludeGroupsAlbumSingle,
      market = None,
      requestedLimit = 10,
      nextOffset = 5,
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
    ).toArtistReleaseSyncQueueDbRow

    val temporaryFailureQueueRow = ArtistReleaseSyncQueueSource(
      spotifyArtistCode = TemporaryFailureArtistCode,
      syncScope = SyncScopeIncremental,
      status = QueueJobStatus.Processing,
      includeGroups = IncludeGroupsAlbumSingle,
      market = None,
      requestedLimit = 10,
      nextOffset = 10,
      nextAttemptAt = Some(fixedNow.minus(1.minute)),
      lastAttemptedAt = Some(fixedNow),
      completedAt = None,
      attemptCount = 2,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "temporary-failure-lock-token",
      lockedUntil = Some(fixedNow.plus(30.minutes)),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 6L
    ).toArtistReleaseSyncQueueDbRow

    val blockedQueueRow = ArtistReleaseSyncQueueSource(
      spotifyArtistCode = BlockedArtistCode,
      syncScope = SyncScopeIncremental,
      status = QueueJobStatus.Processing,
      includeGroups = IncludeGroupsAlbumSingle,
      market = None,
      requestedLimit = 10,
      nextOffset = 10,
      nextAttemptAt = Some(fixedNow.minus(1.minute)),
      lastAttemptedAt = Some(fixedNow),
      completedAt = None,
      attemptCount = 2,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "blocked-lock-token",
      lockedUntil = Some(fixedNow.plus(30.minutes)),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 7L
    ).toArtistReleaseSyncQueueDbRow
  }
}
