package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_events_sync

import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSource, UserFollowedArtistSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.UserFollowedArtistDbRow
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleasesWriter, SpotifyUserWriter, UserFollowedArtistsWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.LocalDate

class UserNewReleaseEventsSyncHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport {
  private given LoggingContext = LoggingContext("user-new-release-events-sync-handler-spec")

  private val userWriter = new SpotifyUserWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter
  private val artistReleasesWriter = new ArtistReleasesWriter

  Feature("User new release events sync job handler") {
    Scenario("フォロー中アーティストの未検出リリースからユーザー別新着リリース履歴を作成する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.UserRow)
        followedArtistWriter.write(Rows.followedArtistRow(userId))
        artistReleasesWriter.write(Rows.ArtistReleaseRow)
      }
      val injector = createInjector(fixedNow)
      val handler = injector.getInstance(classOf[UserNewReleaseEventsSyncHandler])

      unsafeRun(handler.handle())

      val rows = databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
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

      assert(
        rows == Seq(
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
