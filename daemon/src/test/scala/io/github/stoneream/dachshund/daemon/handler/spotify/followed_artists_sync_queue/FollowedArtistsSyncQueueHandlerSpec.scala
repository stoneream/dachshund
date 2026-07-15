package io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync_queue

import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.FollowedArtistSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{FollowedArtistSyncQueueSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.FollowedArtistSyncQueueDbRow
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{FollowedArtistSyncQueueWriter, SpotifyUserWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.LocalDate

class FollowedArtistsSyncQueueHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport {
  private given LoggingContext = LoggingContext("followed-artists-sync-queue-handler-spec")

  private val userWriter = new SpotifyUserWriter
  private val queueWriter = new FollowedArtistSyncQueueWriter

  Feature("Followed artists sync queue job handler") {
    Scenario("有効ユーザーのうち同日 queue が未作成のユーザーだけ同期 queue を作成する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        userWriter.write(Rows.ActiveUserRow)
        userWriter.write(Rows.DisabledUserRow)
        val queuedUserId = userWriter.write(Rows.QueuedUserRow)
        queueWriter.write(Rows.existingQueueRow(queuedUserId))
      }
      val injector = createInjector(fixedNow)
      val handler = injector.getInstance(classOf[FollowedArtistsSyncQueueHandler])

      unsafeRun(handler.handle())

      val rows = databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        sql"""
          select
            u.user_name,
            fasq.sync_date,
            fasq.status,
            fasq.requested_limit,
            fasq.next_attempt_at,
            fasq.attempt_count,
            fasq.lock_token,
            fasq.lock_version
          from followed_artist_sync_queue fasq
            inner join user u on u.id = fasq.user_id
          order by u.user_name asc
        """
          .map { rs =>
            FollowedArtistSyncQueueRow(
              userName = rs.string("user_name"),
              syncDate = rs.localDate("sync_date"),
              status = rs.string("status"),
              requestedLimit = rs.int("requested_limit"),
              nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
              attemptCount = rs.int("attempt_count"),
              lockToken = rs.string("lock_token"),
              lockVersion = rs.long("lock_version")
            )
          }
          .list
          .apply()
      }

      assert(
        rows == Seq(
          FollowedArtistSyncQueueRow(
            userName = "active-user",
            syncDate = fixedNow.toLocalDate,
            status = QueueJobStatus.Scheduled.dbValue,
            requestedLimit = 50,
            nextAttemptAt = Some(fixedNow),
            attemptCount = 0,
            lockToken = "",
            lockVersion = 0L
          ),
          FollowedArtistSyncQueueRow(
            userName = "queued-user",
            syncDate = fixedNow.toLocalDate,
            status = QueueJobStatus.Scheduled.dbValue,
            requestedLimit = 50,
            nextAttemptAt = Some(fixedNow),
            attemptCount = 0,
            lockToken = "",
            lockVersion = 0L
          )
        )
      )
    }
  }

  private final case class FollowedArtistSyncQueueRow(
      userName: String,
      syncDate: LocalDate,
      status: String,
      requestedLimit: Int,
      nextAttemptAt: Option[BusinessDateTime],
      attemptCount: Int,
      lockToken: String,
      lockVersion: Long
  )

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  private object Rows {
    val ActiveUserRow = UserSource(
      userName = "active-user",
      displayName = "Active User",
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

    val DisabledUserRow = UserSource(
      userName = "disabled-user",
      displayName = "Disabled User",
      timeZone = "Asia/Tokyo",
      enabled = 0L,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserDbRow

    val QueuedUserRow = UserSource(
      userName = "queued-user",
      displayName = "Queued User",
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

    def existingQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
      FollowedArtistSyncQueueSource(
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
  }
}
