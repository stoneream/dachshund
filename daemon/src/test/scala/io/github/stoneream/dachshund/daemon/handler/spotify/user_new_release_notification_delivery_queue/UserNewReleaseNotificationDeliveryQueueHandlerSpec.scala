package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_notification_delivery_queue

import com.google.inject.AbstractModule
import io.github.stoneream.dachshund.daemon.config.UserNewReleaseNotificationDeliveryQueueJobConfig
import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.ReleaseTrackDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseNotificationDeliveryQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationRefreshQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSource, ReleaseTrackSource, UserNewReleaseNotificationDeliveryQueueSource, UserPlaylistSettingSource, UserSource, UserSpotifyAuthorizationRefreshQueueSource, UserSpotifyAuthorizationSource}
import io.github.stoneream.dachshund.infra.db.generated.{ArtistReleaseDbRow, ReleaseTrackDbRow, UserDbRow, UserNewReleaseNotificationDeliveryQueueDbRow, UserPlaylistSettingDbRow, UserSpotifyAuthorizationDbRow, UserSpotifyAuthorizationRefreshQueueDbRow}
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleasesWriter, SpotifyAuthorizationRefreshQueueWriter, SpotifyAuthorizationWriter, SpotifyUserWriter, UserNewReleaseNotificationDeliveryQueueWriter, UserPlaylistSettingWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.{EncryptedSpotifyToken, SpotifyTokenEncryptionAad, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.{PlaylistUsageType, QueueJobStatus, ReleaseNotificationType}
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyAddItemsToPlaylistResult
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future
import scala.concurrent.duration.*

class UserNewReleaseNotificationDeliveryQueueHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport with IdiomaticMockito {
  import UserNewReleaseNotificationDeliveryQueueHandlerSpec.*

  private given LoggingContext = LoggingContext("user-new-release-notification-delivery-queue-handler-spec")

  private lazy val tokenEncryptor = new SpotifyTokenEncryptor(testApplicationConfig)
  private val userWriter = new SpotifyUserWriter
  private val authorizationWriter = new SpotifyAuthorizationWriter
  private val refreshQueueWriter = new SpotifyAuthorizationRefreshQueueWriter
  private val artistReleasesWriter = new ArtistReleasesWriter
  private val playlistSettingWriter = new UserPlaylistSettingWriter
  private val queueWriter = new UserNewReleaseNotificationDeliveryQueueWriter

  Feature("User new release notification delivery queue job handler") {
    Scenario("release track を playlist に追加して queue を完了する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-success-user"))
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-success"))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-success", totalTracksCount = 2))
        artistReleasesWriter.writeReleaseTrack(Rows.releaseTrackRow(releaseId, "success-1", discNumber = 1, trackNumber = 1))
        artistReleasesWriter.writeReleaseTrack(Rows.releaseTrackRow(releaseId, "success-2", discNumber = 1, trackNumber = 2))
        val eventId = writeNewReleaseEvent(userId, releaseId, "release-success")
        queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId))
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.addItemsToPlaylist(
        "current-access-token",
        "playlist-success",
        Seq("spotify:track:success-1", "spotify:track:success-2")
      )(using *[LoggingContext]) returns
        Future.successful(SpotifyAddItemsToPlaylistResult("snapshot-success"))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        queueRows() == Seq(
          QueueRow(
            status = QueueJobStatus.Succeeded.dbValue,
            nextAttemptAt = None,
            attemptCount = 0,
            lastAttemptedAt = Some(fixedNow),
            completedAt = Some(fixedNow),
            lastFailedAt = None,
            lastErrorType = "",
            lockToken = "",
            lockedUntil = None,
            spotifySnapshotId = "snapshot-success",
            lockVersion = 2L
          )
        )
      )
    }

    Scenario("release track が存在しない場合は blocked として保存する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-no-tracks-user"))
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-no-tracks"))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-no-tracks", totalTracksCount = 0))
        val eventId = writeNewReleaseEvent(userId, releaseId, "release-no-tracks")
        queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId))
      }
      val handler = createHandler(mock[SpotifyOAuthClient], mock[SpotifyClient])

      unsafeRun(handler.handle())

      assert(
        queueRows().map(row => (row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.lockToken, row.lockVersion)) ==
          Seq((QueueJobStatus.Blocked.dbValue, None, Some(fixedNow), "release_tracks_not_found", "", 2L))
      )
    }

    Scenario("Spotify API 401 は token を強制 refresh して再試行する") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-unauthorized-user"))
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-unauthorized"))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-unauthorized", totalTracksCount = 1))
        artistReleasesWriter.writeReleaseTrack(Rows.releaseTrackRow(releaseId, "unauthorized-1", discNumber = 1, trackNumber = 1))
        val eventId = writeNewReleaseEvent(userId, releaseId, "release-unauthorized")
        queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId))
        userId
      }
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.refreshAccessToken("current-refresh-token", "spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
        Future.successful(TokenResponse("refreshed-access-token", "Bearer", 3600, Some("refreshed-refresh-token"), Some(Rows.RequiredScopeText)))
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.addItemsToPlaylist("current-access-token", "playlist-unauthorized", Seq("spotify:track:unauthorized-1"))(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.Unauthorized(new RuntimeException("unauthorized")))
      spotifyClient.addItemsToPlaylist("refreshed-access-token", "playlist-unauthorized", Seq("spotify:track:unauthorized-1"))(using *[LoggingContext]) returns
        Future.successful(SpotifyAddItemsToPlaylistResult("snapshot-unauthorized"))
      val handler = createHandler(spotifyOAuthClient, spotifyClient)

      unsafeRun(handler.handle())

      assert(
        queueRows().map(row => (row.status, row.completedAt, row.spotifySnapshotId)) == Seq(
          (QueueJobStatus.Succeeded.dbValue, Some(fixedNow), "snapshot-unauthorized")
        )
      )
      assert(
        authorizationRows().map(row => (row.userId, row.scopeText, row.accessTokenExpiresAt, row.lastRefreshedAt, row.lockVersion)) ==
          Seq((userId, Rows.RequiredScopeText, fixedNow.plus(3600.seconds), Some(fixedNow), 1L))
      )
      assert(
        authorizationRefreshQueueRows().map(row => (row.status, row.nextAttemptAt, row.completedAt, row.lastErrorType, row.lockVersion)) ==
          Seq((QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(3300.seconds)), Some(fixedNow), "", 1L))
      )
    }

    Scenario("Spotify API 403 は blocked として保存する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-forbidden-user"))
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-forbidden"))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-forbidden", totalTracksCount = 1))
        artistReleasesWriter.writeReleaseTrack(Rows.releaseTrackRow(releaseId, "forbidden-1", discNumber = 1, trackNumber = 1))
        val eventId = writeNewReleaseEvent(userId, releaseId, "release-forbidden")
        queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId))
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.addItemsToPlaylist("current-access-token", "playlist-forbidden", Seq("spotify:track:forbidden-1"))(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.Forbidden(new RuntimeException("forbidden")))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        queueRows().map(row => (row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.lockToken)) ==
          Seq((QueueJobStatus.Blocked.dbValue, None, Some(fixedNow), "insufficient_scope", ""))
      )
    }

    Scenario("Spotify access token 一時失敗は token refresh の retry 時刻で scheduled に戻す") {
      val tokenRetryAt = fixedNow.plus(2.minutes)
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-token-temporary-failure-user"))
        val authorizationId = writeAuthorization(userId, "current-access-token", "current-refresh-token")
        markAuthorizationRefreshQueueTemporaryFailure(authorizationId, tokenRetryAt, "rate_limited")
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-token-temporary-failure"))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-token-temporary-failure", totalTracksCount = 1))
        artistReleasesWriter.writeReleaseTrack(Rows.releaseTrackRow(releaseId, "token-temporary-failure-1", discNumber = 1, trackNumber = 1))
        val eventId = writeNewReleaseEvent(userId, releaseId, "release-token-temporary-failure")
        queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId))
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.addItemsToPlaylist("current-access-token", "playlist-token-temporary-failure", Seq("spotify:track:token-temporary-failure-1"))(using
        *[LoggingContext]
      ) returns
        Future.failed(SpotifyClientException.Unauthorized(new RuntimeException("unauthorized")))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        queueRows().map(row => (row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.attemptCount, row.lockToken, row.lockVersion)) ==
          Seq((QueueJobStatus.Scheduled.dbValue, Some(tokenRetryAt), Some(fixedNow), "rate_limited", 1, "", 2L))
      )
    }

    Scenario("Spotify API rate limit は retry 時刻を保存して scheduled に戻す") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-rate-limited-user"))
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-rate-limited"))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-rate-limited", totalTracksCount = 1))
        artistReleasesWriter.writeReleaseTrack(Rows.releaseTrackRow(releaseId, "rate-limited-1", discNumber = 1, trackNumber = 1))
        val eventId = writeNewReleaseEvent(userId, releaseId, "release-rate-limited")
        queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId, attemptCount = 1))
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.addItemsToPlaylist("current-access-token", "playlist-rate-limited", Seq("spotify:track:rate-limited-1"))(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.RateLimited(Some(10.seconds), new RuntimeException("rate limited")))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        queueRows().map(row => (row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.attemptCount, row.lockToken, row.lockVersion)) ==
          Seq((QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(10.seconds)), Some(fixedNow), "rate_limited", 2, "", 2L))
      )
    }

    Scenario("Spotify API rate limit が最大試行回数に達した場合は blocked として保存する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-rate-limit-exhausted-user"))
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-rate-limit-exhausted"))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-rate-limit-exhausted", totalTracksCount = 1))
        artistReleasesWriter.writeReleaseTrack(Rows.releaseTrackRow(releaseId, "rate-limit-exhausted-1", discNumber = 1, trackNumber = 1))
        val eventId = writeNewReleaseEvent(userId, releaseId, "release-rate-limit-exhausted")
        queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId, attemptCount = 2))
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.addItemsToPlaylist("current-access-token", "playlist-rate-limit-exhausted", Seq("spotify:track:rate-limit-exhausted-1"))(using
        *[LoggingContext]
      ) returns
        Future.failed(SpotifyClientException.RateLimited(Some(10.seconds), new RuntimeException("rate limited")))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        queueRows().map(row => (row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.attemptCount, row.lockToken, row.lockVersion)) ==
          Seq((QueueJobStatus.Blocked.dbValue, None, Some(fixedNow), "rate_limited", 3, "", 2L))
      )
    }

    Scenario("PLAYLIST 以外の通知キューは claim も stale recovery もしない") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("delivery-non-playlist-user"))
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId, "playlist-non-playlist"))

        val scheduledReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-non-playlist-scheduled", totalTracksCount = 1))
        val scheduledEventId = writeNewReleaseEvent(userId, scheduledReleaseId, "release-non-playlist-scheduled")
        writeNotificationQueue(
          userNewReleaseEventId = scheduledEventId,
          releaseNotificationType = "EMAIL",
          playlistSettingId = playlistSettingId,
          status = QueueJobStatus.Scheduled,
          nextAttemptAt = Some(fixedNow),
          attemptCount = 0,
          lockToken = "",
          lockedUntil = None,
          lastAttemptedAt = None,
          lockVersion = 0L
        )

        val staleReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("release-non-playlist-stale", totalTracksCount = 1))
        val staleEventId = writeNewReleaseEvent(userId, staleReleaseId, "release-non-playlist-stale")
        writeNotificationQueue(
          userNewReleaseEventId = staleEventId,
          releaseNotificationType = "EMAIL",
          playlistSettingId = playlistSettingId,
          status = QueueJobStatus.Processing,
          nextAttemptAt = Some(fixedNow.minus(10.minutes)),
          attemptCount = 1,
          lockToken = "stale-non-playlist-lock-token",
          lockedUntil = Some(fixedNow.minus(1.minute)),
          lastAttemptedAt = Some(fixedNow.minus(10.minutes)),
          lockVersion = 2L
        )
      }
      val handler = createHandler(mock[SpotifyOAuthClient], mock[SpotifyClient])

      unsafeRun(handler.handle())

      assert(
        queueRows() == Seq(
          QueueRow(
            status = QueueJobStatus.Scheduled.dbValue,
            nextAttemptAt = Some(fixedNow),
            attemptCount = 0,
            lastAttemptedAt = None,
            completedAt = None,
            lastFailedAt = None,
            lastErrorType = "",
            lockToken = "",
            lockedUntil = None,
            spotifySnapshotId = "",
            lockVersion = 0L
          ),
          QueueRow(
            status = QueueJobStatus.Processing.dbValue,
            nextAttemptAt = Some(fixedNow.minus(10.minutes)),
            attemptCount = 1,
            lastAttemptedAt = Some(fixedNow.minus(10.minutes)),
            completedAt = None,
            lastFailedAt = None,
            lastErrorType = "",
            lockToken = "stale-non-playlist-lock-token",
            lockedUntil = Some(fixedNow.minus(1.minute)),
            spotifySnapshotId = "",
            lockVersion = 2L
          )
        )
      )
    }
  }

  private def createHandler(
      spotifyOAuthClient: SpotifyOAuthClient,
      spotifyClient: SpotifyClient,
      config: Option[UserNewReleaseNotificationDeliveryQueueJobConfig] = None
  ): UserNewReleaseNotificationDeliveryQueueHandler = {
    val module = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[SpotifyOAuthClient]).toInstance(spotifyOAuthClient)
        bind(classOf[SpotifyClient]).toInstance(spotifyClient)
        config.foreach(value => bind(classOf[UserNewReleaseNotificationDeliveryQueueJobConfig]).toInstance(value))
      }
    }
    createInjector(fixedNow, module).getInstance(classOf[UserNewReleaseNotificationDeliveryQueueHandler])
  }

  private def writeAuthorization(
      userId: Long,
      accessToken: String,
      refreshToken: String
  )(using DBSession): Long = {
    authorizationWriter.write(
      Rows.authorizationRow(
        userId = userId,
        encryptedAccessToken = encryptedAccessToken(userId, accessToken),
        encryptedRefreshToken = encryptedRefreshToken(userId, refreshToken)
      )
    )
    val authorizationId = authorizationIdByUserId(userId)
    refreshQueueWriter.write(Rows.authorizationRefreshQueueRow(authorizationId))
    authorizationId
  }

  private def markAuthorizationRefreshQueueTemporaryFailure(
      authorizationId: Long,
      nextAttemptAt: BusinessDateTime,
      lastErrorType: String
  )(using DBSession): Unit =
    sql"""
      update user_spotify_authorization_refresh_queue
      set
        next_attempt_at = {nextAttemptAt},
        attempt_count = 1,
        last_failed_at = {lastFailedAt},
        last_error_type = {lastErrorType},
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        lock_version = lock_version + 1
      where
        authorization_id = {authorizationId}
        and deleted = 0
    """
      .bindByName(
        "authorizationId" -> authorizationId,
        "nextAttemptAt" -> nextAttemptAt.toLocalDateTime,
        "lastFailedAt" -> fixedNow.toLocalDateTime,
        "lastErrorType" -> lastErrorType,
        "updatedAt" -> fixedNow.toLocalDateTime,
        "updatedUser" -> AuditUser.System.dbValue
      )
      .update
      .apply()

  private def encryptedAccessToken(userId: Long, token: String): EncryptedSpotifyToken =
    tokenEncryptor.encrypt(
      token,
      Some(SpotifyTokenEncryptionAad.accessToken(userId, testApplicationConfig.spotify.token.encryptionKeyVersion))
    )

  private def encryptedRefreshToken(userId: Long, token: String): EncryptedSpotifyToken =
    tokenEncryptor.encrypt(
      token,
      Some(SpotifyTokenEncryptionAad.refreshToken(userId, testApplicationConfig.spotify.token.encryptionKeyVersion))
    )

  private def authorizationIdByUserId(userId: Long)(using DBSession): Long =
    sql"select id from user_spotify_authorization where user_id = {userId}"
      .bindByName("userId" -> userId)
      .map(_.long("id"))
      .single
      .apply()
      .get

  private def writeNewReleaseEvent(
      userId: Long,
      artistReleaseId: Long,
      spotifyReleaseCode: String
  )(using DBSession): Long =
    sql"""
      insert into user_new_release_event (
        user_id,
        artist_release_id,
        spotify_release_code,
        source_spotify_artist_code,
        detected_at,
        detection_sync_code,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {userId},
        {artistReleaseId},
        {spotifyReleaseCode},
        {sourceSpotifyArtistCode},
        {detectedAt},
        {detectionSyncCode},
        {createdAt},
        {updatedAt},
        {deletedAt},
        {createdUser},
        {updatedUser},
        {deletedUser},
        {deleted},
        {lockVersion}
      )
    """
      .bindByName(
        "userId" -> userId,
        "artistReleaseId" -> artistReleaseId,
        "spotifyReleaseCode" -> spotifyReleaseCode,
        "sourceSpotifyArtistCode" -> "source-artist-code",
        "detectedAt" -> fixedNow.toLocalDateTime,
        "detectionSyncCode" -> "user-new-release-events-sync",
        "createdAt" -> fixedNow.toLocalDateTime,
        "updatedAt" -> fixedNow.toLocalDateTime,
        "deletedAt" -> Option.empty[LocalDateTime],
        "createdUser" -> AuditUser.System.dbValue,
        "updatedUser" -> AuditUser.System.dbValue,
        "deletedUser" -> AuditUser.Empty.dbValue,
        "deleted" -> 0L,
        "lockVersion" -> 0L
      )
      .updateAndReturnGeneratedKey
      .apply()

  private def writeNotificationQueue(
      userNewReleaseEventId: Long,
      releaseNotificationType: String,
      playlistSettingId: Long,
      status: QueueJobStatus,
      nextAttemptAt: Option[BusinessDateTime],
      attemptCount: Int,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      lockVersion: Long
  )(using DBSession): Long =
    sql"""
      insert into user_new_release_notification_delivery_queue (
        user_new_release_event_id,
        release_notification_type,
        playlist_setting_id,
        status,
        next_attempt_at,
        attempt_count,
        last_failed_at,
        last_error_type,
        lock_token,
        locked_until,
        last_attempted_at,
        completed_at,
        spotify_snapshot_id,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {userNewReleaseEventId},
        {releaseNotificationType},
        {playlistSettingId},
        {status},
        {nextAttemptAt},
        {attemptCount},
        {lastFailedAt},
        {lastErrorType},
        {lockToken},
        {lockedUntil},
        {lastAttemptedAt},
        {completedAt},
        {spotifySnapshotId},
        {createdAt},
        {updatedAt},
        {deletedAt},
        {createdUser},
        {updatedUser},
        {deletedUser},
        {deleted},
        {lockVersion}
      )
    """
      .bindByName(
        "userNewReleaseEventId" -> userNewReleaseEventId,
        "releaseNotificationType" -> releaseNotificationType,
        "playlistSettingId" -> playlistSettingId,
        "status" -> status.dbValue,
        "nextAttemptAt" -> nextAttemptAt.map(_.toLocalDateTime),
        "attemptCount" -> attemptCount,
        "lastFailedAt" -> Option.empty[LocalDateTime],
        "lastErrorType" -> "",
        "lockToken" -> lockToken,
        "lockedUntil" -> lockedUntil.map(_.toLocalDateTime),
        "lastAttemptedAt" -> lastAttemptedAt.map(_.toLocalDateTime),
        "completedAt" -> Option.empty[LocalDateTime],
        "spotifySnapshotId" -> "",
        "createdAt" -> fixedNow.toLocalDateTime,
        "updatedAt" -> fixedNow.toLocalDateTime,
        "deletedAt" -> Option.empty[LocalDateTime],
        "createdUser" -> AuditUser.System.dbValue,
        "updatedUser" -> AuditUser.System.dbValue,
        "deletedUser" -> AuditUser.Empty.dbValue,
        "deleted" -> 0L,
        "lockVersion" -> lockVersion
      )
      .updateAndReturnGeneratedKey
      .apply()

  private def queueRows(): Seq[QueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          status,
          next_attempt_at,
          attempt_count,
          last_attempted_at,
          completed_at,
          last_failed_at,
          last_error_type,
          lock_token,
          locked_until,
          spotify_snapshot_id,
          lock_version
        from user_new_release_notification_delivery_queue
        order by id asc
      """
        .map { rs =>
          QueueRow(
            status = rs.string("status"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
            attemptCount = rs.int("attempt_count"),
            lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
            completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
            lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
            lastErrorType = rs.string("last_error_type"),
            lockToken = rs.string("lock_token"),
            lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
            spotifySnapshotId = rs.string("spotify_snapshot_id"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private def authorizationRows(): Seq[AuthorizationRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          user_id,
          scope_text,
          access_token_expires_at,
          last_refreshed_at,
          lock_version
        from user_spotify_authorization
        order by user_id asc
      """
        .map { rs =>
          AuthorizationRow(
            userId = rs.long("user_id"),
            scopeText = rs.string("scope_text"),
            accessTokenExpiresAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("access_token_expires_at")),
            lastRefreshedAt = rs.localDateTimeOpt("last_refreshed_at").map(BusinessDateTime.fromLocalDateTime),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private def authorizationRefreshQueueRows(): Seq[AuthorizationRefreshQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          status,
          next_attempt_at,
          completed_at,
          last_error_type,
          lock_version
        from user_spotify_authorization_refresh_queue
        order by id asc
      """
        .map { rs =>
          AuthorizationRefreshQueueRow(
            status = rs.string("status"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
            completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
            lastErrorType = rs.string("last_error_type"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private final case class QueueRow(
      status: String,
      nextAttemptAt: Option[BusinessDateTime],
      attemptCount: Int,
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      spotifySnapshotId: String,
      lockVersion: Long
  )

  private final case class AuthorizationRow(
      userId: Long,
      scopeText: String,
      accessTokenExpiresAt: BusinessDateTime,
      lastRefreshedAt: Option[BusinessDateTime],
      lockVersion: Long
  )

  private final case class AuthorizationRefreshQueueRow(
      status: String,
      nextAttemptAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockVersion: Long
  )
}

private object UserNewReleaseNotificationDeliveryQueueHandlerSpec {
  val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  object Rows {
    val RequiredScopeText = "playlist-modify-private playlist-modify-public playlist-read-private user-follow-read"

    def userRow(userName: String): UserDbRow =
      UserSource(
        userName = userName,
        displayName = userName,
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

    def authorizationRow(
        userId: Long,
        encryptedAccessToken: EncryptedSpotifyToken,
        encryptedRefreshToken: EncryptedSpotifyToken
    ): UserSpotifyAuthorizationDbRow =
      UserSpotifyAuthorizationSource(
        userId = userId,
        scopeText = RequiredScopeText,
        accessTokenCipher = encryptedAccessToken.cipherText,
        accessTokenNonce = encryptedAccessToken.nonce,
        accessTokenTag = encryptedAccessToken.tag,
        refreshTokenCipher = encryptedRefreshToken.cipherText,
        refreshTokenNonce = encryptedRefreshToken.nonce,
        refreshTokenTag = encryptedRefreshToken.tag,
        encryptionAlgorithm = encryptedAccessToken.algorithm,
        encryptionKeyVersion = encryptedAccessToken.keyVersion,
        tokenType = "Bearer",
        accessTokenExpiresAt = fixedNow.plus(3600.seconds),
        refreshMarginSeconds = 300,
        lastAuthorizedAt = Some(fixedNow),
        lastRefreshedAt = None,
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserSpotifyAuthorizationDbRow

    def authorizationRefreshQueueRow(authorizationId: Long): UserSpotifyAuthorizationRefreshQueueDbRow =
      UserSpotifyAuthorizationRefreshQueueSource(
        authorizationId = authorizationId,
        status = QueueJobStatus.Scheduled,
        nextAttemptAt = Some(fixedNow),
        attemptCount = 0,
        lastFailedAt = None,
        lastErrorType = "",
        lockToken = "",
        lockedUntil = None,
        lastAttemptedAt = None,
        completedAt = None,
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserSpotifyAuthorizationRefreshQueueDbRow

    def artistReleaseRow(
        spotifyReleaseCode: String,
        totalTracksCount: Int
    ): ArtistReleaseDbRow =
      ArtistReleaseSource(
        spotifyReleaseCode = spotifyReleaseCode,
        sourceSpotifyArtistCode = "source-artist-code",
        releaseName = spotifyReleaseCode,
        releaseType = "ALBUM",
        albumType = "album",
        albumGroup = Some("album"),
        spotifyReleaseUri = s"spotify:album:$spotifyReleaseCode",
        spotifyUrl = "",
        href = "",
        primaryImageUrl = "",
        primaryImageHeight = None,
        primaryImageWidth = None,
        imagesJson = None,
        releaseDateText = "2026-06-21",
        releaseDatePrecision = "day",
        releaseDateAt = Some(LocalDate.of(2026, 6, 21).atStartOfDay()),
        totalTracksCount = Some(totalTracksCount),
        labelName = None,
        normalizedLabelName = None,
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

    def releaseTrackRow(
        artistReleaseId: Long,
        spotifyTrackCode: String,
        discNumber: Int,
        trackNumber: Int
    ): ReleaseTrackDbRow =
      ReleaseTrackSource(
        artistReleaseId = artistReleaseId,
        spotifyTrackCode = spotifyTrackCode,
        trackName = spotifyTrackCode,
        spotifyTrackUri = s"spotify:track:$spotifyTrackCode",
        spotifyUrl = "",
        href = "",
        discNumber = discNumber,
        trackNumber = trackNumber,
        durationMs = None,
        explicit = None,
        isPlayable = None,
        isLocal = None,
        linkedFromSpotifyTrackCode = None,
        linkedFromSpotifyTrackUri = None,
        previewUrl = None,
        externalIdsJson = None,
        isrcCode = None,
        eanCode = None,
        upcCode = None,
        availableMarketsJson = None,
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
      ).toReleaseTrackDbRow

    def playlistSettingRow(
        userId: Long,
        spotifyPlaylistCode: String
    ): UserPlaylistSettingDbRow =
      UserPlaylistSettingSource(
        userId = userId,
        playlistUsageType = PlaylistUsageType.NewReleaseNotification,
        spotifyPlaylistCode = spotifyPlaylistCode,
        spotifyPlaylistUri = s"spotify:playlist:$spotifyPlaylistCode",
        playlistName = spotifyPlaylistCode,
        enabled = 1L,
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserPlaylistSettingDbRow

    def scheduledQueueRow(
        userNewReleaseEventId: Long,
        playlistSettingId: Long,
        attemptCount: Int = 0
    ): UserNewReleaseNotificationDeliveryQueueDbRow =
      UserNewReleaseNotificationDeliveryQueueSource(
        userNewReleaseEventId = userNewReleaseEventId,
        releaseNotificationType = ReleaseNotificationType.Playlist,
        playlistSettingId = playlistSettingId,
        status = QueueJobStatus.Scheduled,
        nextAttemptAt = Some(fixedNow),
        attemptCount = attemptCount,
        lastFailedAt = None,
        lastErrorType = "",
        lockToken = "",
        lockedUntil = None,
        lastAttemptedAt = None,
        completedAt = None,
        spotifySnapshotId = "",
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserNewReleaseNotificationDeliveryQueueDbRow
  }
}
