package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_events_sync

import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSource, UserFollowedArtistSource, UserPlaylistSettingSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.{UserFollowedArtistDbRow, UserPlaylistSettingDbRow}
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleasesWriter, SpotifyUserWriter, UserFollowedArtistsWriter, UserPlaylistSettingWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.{PlaylistUsageType, QueueJobStatus, ReleaseNotificationType}
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.LocalDate

class UserNewReleaseEventsSyncHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport {
  private given LoggingContext = LoggingContext("user-new-release-events-sync-handler-spec")

  private val userWriter = new SpotifyUserWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter
  private val artistReleasesWriter = new ArtistReleasesWriter
  private val playlistSettingWriter = new UserPlaylistSettingWriter

  Feature("User new release events sync job handler") {
    Scenario("playlist 設定がある場合はユーザー別新着リリース履歴と通知 queue を作成する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.UserRow)
        followedArtistWriter.write(Rows.followedArtistRow(userId))
        artistReleasesWriter.write(Rows.ArtistReleaseRow)
        playlistSettingWriter.write(Rows.playlistSettingRow(userId))
      }
      val injector = createInjector(fixedNow)
      val handler = injector.getInstance(classOf[UserNewReleaseEventsSyncHandler])

      unsafeRun(handler.handle())

      assert(
        newReleaseEventRows() == Seq(
          UserNewReleaseEventRow(
            userName = "active-user",
            spotifyReleaseCode = "release-1",
            sourceSpotifyArtistCode = "artist-1",
            detectedAt = fixedNow,
            detectionSyncCode = "user-new-release-events-sync",
            deleted = 0L,
            lockVersion = 0L
          )
        )
      )
      assert(
        notificationQueueRows() == Seq(
          NotificationQueueRow(
            userName = "active-user",
            spotifyReleaseCode = "release-1",
            releaseNotificationType = ReleaseNotificationType.Playlist.dbValue,
            playlistUsageType = PlaylistUsageType.NewReleaseNotification.dbValue,
            status = QueueJobStatus.Scheduled.dbValue,
            nextAttemptAt = fixedNow,
            attemptCount = 0,
            lastAttemptedAt = None,
            lockToken = "",
            deleted = 0L,
            lockVersion = 0L
          )
        )
      )
    }

    Scenario("playlist 設定がない場合はユーザー別新着リリース履歴だけを作成する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.UserRow)
        followedArtistWriter.write(Rows.followedArtistRow(userId))
        artistReleasesWriter.write(Rows.ArtistReleaseRow)
      }
      val injector = createInjector(fixedNow)
      val handler = injector.getInstance(classOf[UserNewReleaseEventsSyncHandler])

      unsafeRun(handler.handle())

      assert(newReleaseEventRows().size == 1)
      assert(notificationQueueRows().isEmpty)
    }

    Scenario("playlist 設定が無効な場合はユーザー別新着リリース履歴だけを作成する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.UserRow)
        followedArtistWriter.write(Rows.followedArtistRow(userId))
        artistReleasesWriter.write(Rows.ArtistReleaseRow)
        playlistSettingWriter.write(Rows.playlistSettingRow(userId, enabled = 0L))
      }
      val injector = createInjector(fixedNow)
      val handler = injector.getInstance(classOf[UserNewReleaseEventsSyncHandler])

      unsafeRun(handler.handle())

      assert(newReleaseEventRows().size == 1)
      assert(notificationQueueRows().isEmpty)
    }
  }

  private final case class UserNewReleaseEventRow(
      userName: String,
      spotifyReleaseCode: String,
      sourceSpotifyArtistCode: String,
      detectedAt: BusinessDateTime,
      detectionSyncCode: String,
      deleted: Long,
      lockVersion: Long
  )

  private final case class NotificationQueueRow(
      userName: String,
      spotifyReleaseCode: String,
      releaseNotificationType: String,
      playlistUsageType: String,
      status: String,
      nextAttemptAt: BusinessDateTime,
      attemptCount: Int,
      lastAttemptedAt: Option[BusinessDateTime],
      lockToken: String,
      deleted: Long,
      lockVersion: Long
  )

  private def newReleaseEventRows(): Seq[UserNewReleaseEventRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          u.user_name,
          ar.spotify_release_code,
          ar.source_spotify_artist_code,
          unre.detected_at,
          unre.detection_sync_code,
          unre.deleted,
          unre.lock_version
        from user_new_release_event unre
          inner join user u on u.id = unre.user_id
          inner join artist_release ar on ar.id = unre.artist_release_id
        order by unre.id asc
      """
        .map { rs =>
          UserNewReleaseEventRow(
            userName = rs.string("user_name"),
            spotifyReleaseCode = rs.string("spotify_release_code"),
            sourceSpotifyArtistCode = rs.string("source_spotify_artist_code"),
            detectedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("detected_at")),
            detectionSyncCode = rs.string("detection_sync_code"),
            deleted = rs.long("deleted"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private def notificationQueueRows(): Seq[NotificationQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          u.user_name,
          ar.spotify_release_code,
          q.release_notification_type,
          ups.playlist_usage_type,
          q.status,
          q.next_attempt_at,
          q.attempt_count,
          q.last_attempted_at,
          q.lock_token,
          q.deleted,
          q.lock_version
        from user_new_release_notification_queue q
          inner join user_new_release_event unre on unre.id = q.user_new_release_event_id
          inner join user u on u.id = unre.user_id
          inner join artist_release ar on ar.id = unre.artist_release_id
          inner join user_playlist_setting ups on ups.id = q.playlist_setting_id
        order by q.id asc
      """
        .map { rs =>
          NotificationQueueRow(
            userName = rs.string("user_name"),
            spotifyReleaseCode = rs.string("spotify_release_code"),
            releaseNotificationType = rs.string("release_notification_type"),
            playlistUsageType = rs.string("playlist_usage_type"),
            status = rs.string("status"),
            nextAttemptAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("next_attempt_at")),
            attemptCount = rs.int("attempt_count"),
            lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
            lockToken = rs.string("lock_token"),
            deleted = rs.long("deleted"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  private object Rows {
    val UserRow = UserSource(
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

    def followedArtistRow(userId: Long): UserFollowedArtistDbRow = UserFollowedArtistSource(
      userId = userId,
      spotifyArtistCode = "artist-1",
      artistName = "Artist 1",
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

    def playlistSettingRow(
        userId: Long,
        enabled: Long = 1L
    ): UserPlaylistSettingDbRow = UserPlaylistSettingSource(
      userId = userId,
      playlistUsageType = PlaylistUsageType.NewReleaseNotification,
      spotifyPlaylistCode = "playlist-1",
      spotifyPlaylistUri = "spotify:playlist:playlist-1",
      playlistName = "New Releases",
      enabled = enabled,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserPlaylistSettingDbRow

    val ArtistReleaseRow = ArtistReleaseSource(
      spotifyReleaseCode = "release-1",
      sourceSpotifyArtistCode = "artist-1",
      releaseName = "Release 1",
      releaseType = "ALBUM",
      albumType = "album",
      albumGroup = Some("album"),
      spotifyReleaseUri = "",
      spotifyUrl = "",
      href = "",
      primaryImageUrl = "",
      primaryImageHeight = None,
      primaryImageWidth = None,
      imagesJson = None,
      releaseDateText = "2026-06-20",
      releaseDatePrecision = "day",
      releaseDateAt = Some(LocalDate.of(2026, 6, 20).atStartOfDay()),
      totalTracksCount = None,
      labelName = Some("Label 1"),
      normalizedLabelName = Some("label 1"),
      externalIdsJson = None,
      upcCode = None,
      eanCode = None,
      isrcCode = None,
      copyrightsJson = None,
      availableMarketsJson = None,
      genresJson = None,
      restrictionsJson = None,
      popularity = None,
      syncedAt = Some(fixedNow),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toArtistReleaseDbRow
  }
}
