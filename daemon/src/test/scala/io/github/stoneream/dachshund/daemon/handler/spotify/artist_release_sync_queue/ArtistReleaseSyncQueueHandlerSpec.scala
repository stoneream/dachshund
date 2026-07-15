package io.github.stoneream.dachshund.daemon.handler.spotify.artist_release_sync_queue

import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSyncQueueSource, UserFollowedArtistSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.UserFollowedArtistDbRow
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleaseSyncQueueWriter, SpotifyUserWriter, UserFollowedArtistsWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

class ArtistReleaseSyncQueueHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport {
  private given LoggingContext = LoggingContext("artist-release-sync-queue-handler-spec")

  private val userWriter = new SpotifyUserWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter
  private val queueWriter = new ArtistReleaseSyncQueueWriter

  Feature("Artist release sync queue job handler") {
    Scenario("有効ユーザーがフォローしていて queue が未作成のアーティストだけ同期 queue を作成する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val activeUserId = userWriter.write(Rows.ActiveUserRow)
        val disabledUserId = userWriter.write(Rows.DisabledUserRow)

        followedArtistWriter.write(Rows.unqueuedArtistRow(activeUserId))
        followedArtistWriter.write(Rows.queuedArtistRow(activeUserId))
        followedArtistWriter.write(Rows.disabledUserArtistRow(disabledUserId))
        queueWriter.write(Rows.ExistingQueueRow)
      }
      val injector = createInjector(fixedNow)
      val handler = injector.getInstance(classOf[ArtistReleaseSyncQueueHandler])

      unsafeRun(handler.handle())

      val rows = databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        sql"""
          select
            spotify_artist_code,
            sync_scope,
            status,
            include_groups,
            requested_limit,
            next_offset,
            next_attempt_at,
            attempt_count,
            lock_token,
            lock_version
          from artist_release_sync_queue
          order by spotify_artist_code asc
        """
          .map { rs =>
            ArtistReleaseSyncQueueRow(
              spotifyArtistCode = rs.string("spotify_artist_code"),
              syncScope = rs.string("sync_scope"),
              status = rs.string("status"),
              includeGroups = rs.string("include_groups"),
              requestedLimit = rs.int("requested_limit"),
              nextOffset = rs.int("next_offset"),
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
          ArtistReleaseSyncQueueRow(
            spotifyArtistCode = "artist-queued",
            syncScope = "INCREMENTAL",
            status = QueueJobStatus.Scheduled.dbValue,
            includeGroups = "album,single",
            requestedLimit = 10,
            nextOffset = 0,
            nextAttemptAt = Some(fixedNow),
            attemptCount = 0,
            lockToken = "",
            lockVersion = 0L
          ),
          ArtistReleaseSyncQueueRow(
            spotifyArtistCode = "artist-unqueued",
            syncScope = "INCREMENTAL",
            status = QueueJobStatus.Scheduled.dbValue,
            includeGroups = "album,single",
            requestedLimit = 10,
            nextOffset = 0,
            nextAttemptAt = Some(fixedNow),
            attemptCount = 0,
            lockToken = "",
            lockVersion = 0L
          )
        )
      )
    }
  }

  private final case class ArtistReleaseSyncQueueRow(
      spotifyArtistCode: String,
      syncScope: String,
      status: String,
      includeGroups: String,
      requestedLimit: Int,
      nextOffset: Int,
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

    def unqueuedArtistRow(userId: Long): UserFollowedArtistDbRow =
      UserFollowedArtistSource(
        userId = userId,
        spotifyArtistCode = "artist-unqueued",
        artistName = "Unqueued Artist",
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

    def queuedArtistRow(userId: Long): UserFollowedArtistDbRow =
      UserFollowedArtistSource(
        userId = userId,
        spotifyArtistCode = "artist-queued",
        artistName = "Queued Artist",
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

    def disabledUserArtistRow(userId: Long): UserFollowedArtistDbRow =
      UserFollowedArtistSource(
        userId = userId,
        spotifyArtistCode = "artist-disabled-user",
        artistName = "Disabled User Artist",
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

    val ExistingQueueRow = ArtistReleaseSyncQueueSource(
      spotifyArtistCode = "artist-queued",
      syncScope = "INCREMENTAL",
      status = QueueJobStatus.Scheduled,
      includeGroups = "album,single",
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

  }
}
