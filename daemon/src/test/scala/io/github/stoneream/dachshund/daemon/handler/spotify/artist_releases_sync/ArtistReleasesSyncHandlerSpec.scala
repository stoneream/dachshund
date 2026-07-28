package io.github.stoneream.dachshund.daemon.handler.spotify.artist_releases_sync

import com.google.inject.{AbstractModule, Module}
import io.github.stoneream.dachshund.daemon.config.ArtistReleasesSyncJobConfig
import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleaseSyncQueueWriter, ArtistReleasesWriter, SpotifyUserWriter, UserFollowedArtistsWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException.SpotifyApiClientError
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import scala.concurrent.Future
import scala.concurrent.duration.*

class ArtistReleasesSyncHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport with IdiomaticMockito {
  import ArtistReleasesSyncHandlerFixture.*

  private given LoggingContext = LoggingContext("artist-releases-sync-handler-spec")

  private val userWriter = new SpotifyUserWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter
  private val queueWriter = new ArtistReleaseSyncQueueWriter
  private val artistReleasesWriter = new ArtistReleasesWriter

  Feature("Artist releases sync job handler") {
    Scenario("claim した queue の 1 ページを保存して次 offset を queue に反映する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ActiveUserRow)
        followedArtistWriter.write(pageArtistFollowedArtistRow(userId))
        queueWriter.write(PageQueueRow)
      }
      val spotifyOAuthClient = clientCredentialsTokenClient()
      val spotifyClient = mock[SpotifyClient]
      spotifyClient
        .getArtistReleaseSummaryPage("artist-page-token", "artist-page", "album,single", Some("JP"), 10, 10)(using *[LoggingContext]) returns
        Future.successful(PageWithNextOffset)
      spotifyClient
        .getArtistRelease("artist-page-token", "artist-page", PageWithNextOffset.releases.head, Some("JP"))(using *[LoggingContext]) returns
        Future.successful(PageRelease)
      val handler = createHandler(spotifyOAuthClient, spotifyClient)

      unsafeRun(handler.handle())

      assert(artistReleaseRows() == Seq(ArtistReleaseRow("release-page", "artist-page", fixedNow, 0L)))
      assert(releaseTrackRows() == Seq(ReleaseTrackRow("release-page-track-1"), ReleaseTrackRow("release-page-track-2")))
      assert(
        artistReleaseQueueRows() == Seq(
          ArtistReleaseQueueRow(
            spotifyArtistCode = "artist-page",
            status = QueueJobStatus.Scheduled.dbValue,
            nextOffset = 20,
            nextAttemptAt = Some(fixedNow),
            lastAttemptedAt = Some(fixedNow),
            completedAt = None,
            attemptCount = 0,
            lastFailedAt = None,
            lastErrorType = "",
            lockToken = "",
            lockedUntil = None,
            lockVersion = 2L
          )
        )
      )
    }

    Scenario("最終ページでは queue を完了にする") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ActiveUserRow)
        followedArtistWriter.write(finalPageArtistFollowedArtistRow(userId))
        queueWriter.write(FinalPageQueueRow)
      }
      val spotifyOAuthClient = clientCredentialsTokenClient()
      val spotifyClient = mock[SpotifyClient]
      spotifyClient
        .getArtistReleaseSummaryPage("artist-page-token", "artist-final", "album,single", Some("JP"), 10, 20)(using *[LoggingContext]) returns
        Future.successful(FinalPage)
      spotifyClient
        .getArtistRelease("artist-page-token", "artist-final", FinalPage.releases.head, Some("JP"))(using *[LoggingContext]) returns
        Future.successful(FinalRelease)
      val handler = createHandler(spotifyOAuthClient, spotifyClient)

      unsafeRun(handler.handle())

      assert(artistReleaseRows().map(_.spotifyReleaseCode) == Seq("release-final"))
      assert(
        artistReleaseQueueRows() == Seq(
          ArtistReleaseQueueRow(
            spotifyArtistCode = "artist-final",
            status = QueueJobStatus.Succeeded.dbValue,
            nextOffset = 0,
            nextAttemptAt = None,
            lastAttemptedAt = Some(fixedNow),
            completedAt = Some(fixedNow),
            attemptCount = 0,
            lastFailedAt = None,
            lastErrorType = "",
            lockToken = "",
            lockedUntil = None,
            lockVersion = 2L
          )
        )
      )
    }

    Scenario("既存の release を含むページでも保存をスキップして queue を進める") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ActiveUserRow)
        followedArtistWriter.write(existingReleaseArtistFollowedArtistRow(userId))
        queueWriter.write(ExistingReleaseQueueRow)
        artistReleasesWriter.write(ExistingReleaseRow)
      }
      val spotifyOAuthClient = clientCredentialsTokenClient()
      val spotifyClient = mock[SpotifyClient]
      spotifyClient
        .getArtistReleaseSummaryPage("artist-page-token", "artist-existing", "album,single", Some("JP"), 10, 10)(using *[LoggingContext]) returns
        Future.successful(ExistingReleasePage)
      val handler = createHandler(spotifyOAuthClient, spotifyClient)

      unsafeRun(handler.handle())

      assert(artistReleaseRows().map(_.spotifyReleaseCode) == Seq("release-existing"))
      assert(releaseTrackRows().isEmpty)
      assert(
        artistReleaseQueueRows().map(row => (row.spotifyArtistCode, row.status, row.nextOffset)) ==
          Seq(("artist-existing", QueueJobStatus.Scheduled.dbValue, 20))
      )
    }

    Scenario("401 の場合は client credentials token を強制 refresh して同じページを 1 回だけ再取得する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ActiveUserRow)
        followedArtistWriter.write(unauthorizedArtistFollowedArtistRow(userId))
        queueWriter.write(UnauthorizedQueueRow)
      }
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      (
        spotifyOAuthClient.requestClientCredentialsAccessToken("spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
          Future.successful(TokenResponse("first-token", "Bearer", 3600, None, None))
      ).andThen(Future.successful(TokenResponse("refreshed-token", "Bearer", 3600, None, None)))
      val spotifyClient = mock[SpotifyClient]
      spotifyClient
        .getArtistReleaseSummaryPage("first-token", "artist-unauthorized", "album,single", Some("JP"), 10, 0)(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.Unauthorized(new RuntimeException("unauthorized")))
      spotifyClient
        .getArtistReleaseSummaryPage("refreshed-token", "artist-unauthorized", "album,single", Some("JP"), 10, 0)(using *[LoggingContext]) returns
        Future.successful(UnauthorizedRetryPage)
      spotifyClient
        .getArtistRelease("refreshed-token", "artist-unauthorized", UnauthorizedRetryPage.releases.head, Some("JP"))(using *[LoggingContext]) returns
        Future.successful(UnauthorizedRetryRelease)
      val handler = createHandler(spotifyOAuthClient, spotifyClient)

      unsafeRun(handler.handle())

      assert(artistReleaseRows().map(_.spotifyReleaseCode) == Seq("release-unauthorized-retry"))
      assert(
        artistReleaseQueueRows().map(row => (row.spotifyArtistCode, row.status, row.completedAt, row.lastErrorType)) ==
          Seq(("artist-unauthorized", QueueJobStatus.Succeeded.dbValue, Some(fixedNow), ""))
      )
    }

    Scenario("client credentials の invalid_client は blocked として queue に保存する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ActiveUserRow)
        followedArtistWriter.write(invalidClientArtistFollowedArtistRow(userId))
        queueWriter.write(InvalidClientQueueRow)
      }
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.requestClientCredentialsAccessToken("spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
        Future.failed(
          SpotifyOAuthClientException.ClientCredentialsTokenRequestFailed(
            SpotifyApiClientError(
              endpoint = "spotify-client-credentials-token",
              statusCode = 401,
              errorCode = Some("invalid_client"),
              errorDescription = Some("invalid client")
            )
          )
        )
      val handler = createHandler(spotifyOAuthClient, mock[SpotifyClient])

      unsafeRun(handler.handle())

      assert(artistReleaseRows().isEmpty)
      assert(
        artistReleaseQueueRows().map(row => (row.spotifyArtistCode, row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.lockToken)) ==
          Seq(("artist-invalid-client", QueueJobStatus.Blocked.dbValue, None, Some(fixedNow), "invalid_client", ""))
      )
    }

    Scenario("rate limit は一時失敗として retry 時刻を保存し、同じ batch の後続 target も延期する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ActiveUserRow)
        followedArtistWriter.write(rateLimitedArtistFollowedArtistRow(userId))
        followedArtistWriter.write(rateLimitedTailArtistFollowedArtistRow(userId))
        queueWriter.write(RateLimitedQueueRow)
        queueWriter.write(RateLimitedTailQueueRow)
      }
      val spotifyOAuthClient = clientCredentialsTokenClient()
      val spotifyClient = mock[SpotifyClient]
      spotifyClient
        .getArtistReleaseSummaryPage("artist-page-token", "artist-rate-limited", "album,single", Some("JP"), 10, 0)(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.RateLimited(Some(10.seconds), new RuntimeException("rate limited")))
      val handler = createHandler(
        spotifyOAuthClient,
        spotifyClient,
        Some(testDaemonConfig.jobs.artistReleasesSync.copy(batchSize = 2))
      )

      unsafeRun(handler.handle())

      assert(artistReleaseRows().isEmpty)
      assert(
        artistReleaseQueueRows().map(row => (row.spotifyArtistCode, row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.attemptCount)) ==
          Seq(
            ("artist-rate-limited", QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(10.seconds)), Some(fixedNow), "rate_limited", 3),
            ("artist-rate-limited-tail", QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(10.seconds)), Some(fixedNow), "rate_limited", 1)
          )
      )
    }

    Scenario("ページ保存が想定外の失敗になった場合は対象 queue を一時失敗にして後続 target を処理する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ActiveUserRow)
        followedArtistWriter.write(unexpectedFailureFirstArtistFollowedArtistRow(userId))
        followedArtistWriter.write(unexpectedFailureSecondArtistFollowedArtistRow(userId))
        queueWriter.write(UnexpectedFailureFirstQueueRow)
        queueWriter.write(UnexpectedFailureSecondQueueRow)
      }
      val spotifyOAuthClient = clientCredentialsTokenClient()
      val spotifyClient = mock[SpotifyClient]
      spotifyClient
        .getArtistReleaseSummaryPage("artist-page-token", "artist-unexpected-first", "album,single", Some("JP"), 10, 0)(using *[LoggingContext]) returns
        Future.successful(PageWithDuplicateTracks)
      spotifyClient
        .getArtistRelease("artist-page-token", "artist-unexpected-first", PageWithDuplicateTracks.releases.head, Some("JP"))(using *[LoggingContext]) returns
        Future.successful(ReleaseWithDuplicateTracks)
      spotifyClient
        .getArtistReleaseSummaryPage("artist-page-token", "artist-unexpected-second", "album,single", Some("JP"), 10, 0)(using *[LoggingContext]) returns
        Future.successful(UnexpectedFailureSecondPage)
      spotifyClient
        .getArtistRelease("artist-page-token", "artist-unexpected-second", UnexpectedFailureSecondPage.releases.head, Some("JP"))(using
          *[LoggingContext]
        ) returns
        Future.successful(UnexpectedFailureSecondRelease)
      val handler = createHandler(
        spotifyOAuthClient,
        spotifyClient,
        Some(testDaemonConfig.jobs.artistReleasesSync.copy(batchSize = 2))
      )

      unsafeRun(handler.handle())

      assert(artistReleaseRows().map(_.spotifyReleaseCode) == Seq("release-unexpected-second"))
      assert(releaseTrackRows().map(_.spotifyTrackCode) == Seq("release-unexpected-second-track-1"))
      assert(
        artistReleaseQueueRows().map(row => (row.spotifyArtistCode, row.status, row.lastErrorType, row.completedAt, row.lockToken)) == Seq(
          ("artist-unexpected-first", QueueJobStatus.Scheduled.dbValue, "unknown", None, ""),
          ("artist-unexpected-second", QueueJobStatus.Succeeded.dbValue, "", Some(fixedNow), "")
        )
      )
      val failedQueue = artistReleaseQueueRows().find(_.spotifyArtistCode == "artist-unexpected-first").get
      assert(failedQueue.nextAttemptAt.exists(_.isAfter(fixedNow)))
      assert(failedQueue.lastFailedAt == Some(fixedNow))
    }
  }

  private def createHandler(
      spotifyOAuthClient: SpotifyOAuthClient,
      spotifyClient: SpotifyClient,
      config: Option[ArtistReleasesSyncJobConfig] = None
  ): ArtistReleasesSyncHandler = {
    val module = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[SpotifyOAuthClient]).toInstance(spotifyOAuthClient)
        bind(classOf[SpotifyClient]).toInstance(spotifyClient)
        config.foreach(value => bind(classOf[ArtistReleasesSyncJobConfig]).toInstance(value))
      }
    }
    createInjector(fixedNow, module).getInstance(classOf[ArtistReleasesSyncHandler])
  }

  private def clientCredentialsTokenClient(): SpotifyOAuthClient = {
    val spotifyOAuthClient = mock[SpotifyOAuthClient]
    spotifyOAuthClient.requestClientCredentialsAccessToken("spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
      Future.successful(TokenResponse("artist-page-token", "Bearer", 3600, None, None))
    spotifyOAuthClient
  }

  private def artistReleaseRows(): Seq[ArtistReleaseRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          spotify_release_code,
          source_spotify_artist_code,
          synced_at,
          deleted
        from artist_release
        order by spotify_release_code asc
      """
        .map { rs =>
          ArtistReleaseRow(
            spotifyReleaseCode = rs.string("spotify_release_code"),
            sourceSpotifyArtistCode = rs.string("source_spotify_artist_code"),
            syncedAt = rs.localDateTimeOpt("synced_at").map(BusinessDateTime.fromLocalDateTime).get,
            deleted = rs.long("deleted")
          )
        }
        .list
        .apply()
    }

  private def releaseTrackRows(): Seq[ReleaseTrackRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          spotify_track_code
        from release_track
        order by spotify_track_code asc
      """
        .map(rs => ReleaseTrackRow(rs.string("spotify_track_code")))
        .list
        .apply()
    }

  private def artistReleaseQueueRows(): Seq[ArtistReleaseQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          spotify_artist_code,
          status,
          next_offset,
          next_attempt_at,
          last_attempted_at,
          completed_at,
          attempt_count,
          last_failed_at,
          last_error_type,
          lock_token,
          locked_until,
          lock_version
        from artist_release_sync_queue
        order by spotify_artist_code asc
      """
        .map { rs =>
          ArtistReleaseQueueRow(
            spotifyArtistCode = rs.string("spotify_artist_code"),
            status = rs.string("status"),
            nextOffset = rs.int("next_offset"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
            lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
            completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
            attemptCount = rs.int("attempt_count"),
            lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
            lastErrorType = rs.string("last_error_type"),
            lockToken = rs.string("lock_token"),
            lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private final case class ArtistReleaseRow(
      spotifyReleaseCode: String,
      sourceSpotifyArtistCode: String,
      syncedAt: BusinessDateTime,
      deleted: Long
  )

  private final case class ReleaseTrackRow(spotifyTrackCode: String)

  private final case class ArtistReleaseQueueRow(
      spotifyArtistCode: String,
      status: String,
      nextOffset: Int,
      nextAttemptAt: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      attemptCount: Int,
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      lockVersion: Long
  )
}
