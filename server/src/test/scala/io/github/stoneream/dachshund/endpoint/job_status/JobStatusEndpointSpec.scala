package io.github.stoneream.dachshund.endpoint.job_status

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.endpoint.home.HomeEndpointFixture.*
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.FollowedArtistSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseNotificationQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationRefreshQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSyncQueueSource, FollowedArtistSyncQueueSource, UserNewReleaseNotificationQueueSource, UserPlaylistSettingSource, UserSpotifyAuthorizationRefreshQueueSource, UserSpotifyAuthorizationSource}
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleaseSyncQueueWriter, ArtistReleasesWriter, FollowedArtistSyncQueueWriter, SpotifyAuthorizationRefreshQueueWriter, SpotifyAuthorizationWriter, SpotifyUserWriter, UserFollowedArtistsWriter, UserNewReleaseEventsWriter, UserNewReleaseNotificationQueueWriter, UserPlaylistSettingWriter, UserSessionTokenWriter}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.model.{PlaylistUsageType, QueueJobStatus, ReleaseNotificationType}
import io.github.stoneream.dachshund.module.{ApplicationModule, DatabaseInitializer}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderImpl}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientImpl}
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientImpl}
import io.github.stoneream.dachshund.test.lib.PlayApplicationDatabaseSupport
import io.github.stoneream.dachshund.usecase.job_status.context.JobStatusJob
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.OptionValues
import org.scalatest.featurespec.AnyFeatureSpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import scalikejdbc.*

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.duration.*

class JobStatusEndpointSpec extends AnyFeatureSpec with PlayApplicationDatabaseSupport with OptionValues with IdiomaticMockito {
  private val dateTimeService = mock[DateTimeService]
  dateTimeService.now() returns fixedNow

  override protected lazy val app: Application = GuiceApplicationBuilder()
    .disable[ApplicationModule]
    .overrides(
      bind[ApplicationConfig].toInstance(testApplicationConfig),
      bind[DateTimeService].toInstance(dateTimeService),
      bind[SpotifyOAuthClient].to[SpotifyOAuthClientImpl],
      bind[SpotifyUserProfileClient].to[SpotifyUserProfileClientImpl],
      bind[SpotifyAuthorizationCodeAccessTokenProvider].to[SpotifyAuthorizationCodeAccessTokenProviderImpl],
      bind[SpotifyClient].to[SpotifyClientImpl],
      bind[DatabaseInitializer].toSelf.eagerly()
    )
    .build()

  private val userWriter = new SpotifyUserWriter
  private val userSessionTokenWriter = new UserSessionTokenWriter
  private val authorizationWriter = new SpotifyAuthorizationWriter
  private val authorizationRefreshQueueWriter = new SpotifyAuthorizationRefreshQueueWriter
  private val followedArtistSyncQueueWriter = new FollowedArtistSyncQueueWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter
  private val artistReleaseSyncQueueWriter = new ArtistReleaseSyncQueueWriter
  private val artistReleasesWriter = new ArtistReleasesWriter
  private val userNewReleaseEventsWriter = new UserNewReleaseEventsWriter
  private val playlistSettingWriter = new UserPlaylistSettingWriter
  private val notificationQueueWriter = new UserNewReleaseNotificationQueueWriter
  private val UserNewReleaseEventsSyncDetectionSyncCode = "user-new-release-events-sync"

  Feature("Job status endpoint") {
    Scenario("未ログインの場合は Spotify ログインへ redirect する") {
      val result = route(app, FakeRequest(GET, "/job/status/followed-artists-sync").withHeaders(HOST -> "localhost:9000")).value

      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).value == "/spotify/auth/login")
    }

    Scenario("未ログインの場合は job status index も Spotify ログインへ redirect する") {
      val result = route(app, FakeRequest(GET, "/job/status").withHeaders(HOST -> "localhost:9000")).value

      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).value == "/spotify/auth/login")
    }

    Scenario("未ログインの場合は page が不正でも Spotify ログインへ redirect する") {
      val result = route(app, FakeRequest(GET, "/job/status/followed-artists-sync?page=abc").withHeaders(HOST -> "localhost:9000")).value

      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).value == "/spotify/auth/login")
    }

    Scenario("/job/status はログインユーザーにジョブ一覧を表示する") {
      val loggedInUser = writeLoggedInUserSession()
      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status")).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assert(html.contains("""<h1>Job status</h1>"""))
      assert(html.contains("""<ul class="job-status-list">"""))
      JobStatusJob.All.foreach { job =>
        assert(html.contains(job.title))
        assert(html.contains(s"""<span class="job-status-list-name">${job.name}</span>"""))
        assert(html.contains(s"""href="/job/status/${job.name}""""))
      }
      assert(html.contains("""<a class="site-nav-link is-active" href="/job/status">Job status</a>"""))
    }

    Scenario("ログインユーザーには followed-artists-sync の集計と詳細を表示する") {
      val loggedInUser = writeLoggedInUserSession()

      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        followedArtistSyncQueueWriter.write(followedArtistsQueueRow(loggedInUser.userId, QueueJobStatus.Scheduled, LocalDate.of(2026, 7, 8)))
        followedArtistSyncQueueWriter.write(
          followedArtistsQueueRow(
            loggedInUser.userId,
            QueueJobStatus.Failed,
            LocalDate.of(2026, 7, 7),
            lastErrorType = "RateLimited"
          )
        )
        followedArtistSyncQueueWriter.write(
          followedArtistsQueueRow(
            loggedInUser.userId,
            QueueJobStatus.Blocked,
            LocalDate.of(2026, 7, 6),
            lastErrorType = "Forbidden",
            deleted = 1L
          )
        )
      }

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/followed-artists-sync")).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assert(html.contains("""<h1>Followed artists sync</h1>"""))
      assert(html.contains("""<nav class="job-status-breadcrumb" aria-label="Breadcrumb">"""))
      assert(html.contains("""<li><a href="/job/status">Job status</a></li>"""))
      assert(html.contains("""<li aria-current="page">Followed artists sync</li>"""))
      assert(!html.contains("Other jobs"))
      assert(!html.contains("""<ul class="job-status-list">"""))
      assertQueueStatusSummaryIsHidden(html)
      assertSummaryHasNoCountColumn(html)
      assertStatusBadge(html, "SCHEDULED")
      assertStatusBadge(html, "FAILED")
      assert(html.contains(s"user_id=${loggedInUser.userId}, sync_date=2026-07-08"))
      assert(html.contains(s"user_id=${loggedInUser.userId}, sync_date=2026-07-07"))
      assert(html.contains("RateLimited"))
      assert(!html.contains("Forbidden"))
      assert(html.contains("""<a class="site-nav-link is-active" href="/job/status">Job status</a>"""))
    }

    Scenario("status filter で詳細一覧だけを絞り込む") {
      val loggedInUser = writeLoggedInUserSession()

      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        followedArtistSyncQueueWriter.write(followedArtistsQueueRow(loggedInUser.userId, QueueJobStatus.Scheduled, LocalDate.of(2026, 7, 8)))
        followedArtistSyncQueueWriter.write(
          followedArtistsQueueRow(
            loggedInUser.userId,
            QueueJobStatus.Failed,
            LocalDate.of(2026, 7, 7),
            lastErrorType = "TemporaryFailure"
          )
        )
      }

      val result = route(
        app,
        loggedInGetRequest(loggedInUser.sessionToken, "/job/status/followed-artists-sync?status=FAILED&status=BLOCKED")
      ).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assertQueueStatusSummaryIsHidden(html)
      assertSummaryHasNoCountColumn(html)
      assert(html.contains("""value="FAILED" checked"""))
      assert(html.contains("""value="BLOCKED" checked"""))
      assert(!html.contains("""value="SCHEDULED" checked"""))
      assert(html.contains(s"user_id=${loggedInUser.userId}, sync_date=2026-07-07"))
      assert(!html.contains(s"user_id=${loggedInUser.userId}, sync_date=2026-07-08"))
    }

    Scenario("page query で詳細一覧をページングし status filter を維持する") {
      val loggedInUser = writeLoggedInUserSession()
      val firstDate = LocalDate.of(2026, 1, 1)
      val lastDate = firstDate.plusDays(100)
      val scheduledDate = firstDate.plusDays(101)

      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        (0L to 100L).foreach { dayOffset =>
          followedArtistSyncQueueWriter.write(
            followedArtistsQueueRow(
              loggedInUser.userId,
              QueueJobStatus.Failed,
              firstDate.plusDays(dayOffset),
              lastErrorType = s"TemporaryFailure-$dayOffset"
            )
          )
        }
        followedArtistSyncQueueWriter.write(
          followedArtistsQueueRow(loggedInUser.userId, QueueJobStatus.Scheduled, scheduledDate)
        )
      }

      val page1Result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/followed-artists-sync?status=FAILED")).value
      val page2Result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/followed-artists-sync?status=FAILED&page=2")).value
      val page1Html = contentAsString(page1Result)
      val page2Html = contentAsString(page2Result)

      assert(status(page1Result) == OK)
      assert(page1Html.contains("全 101 件 / Page 1 / 2"))
      assert(page1Html.contains(s"user_id=${loggedInUser.userId}, sync_date=$lastDate"))
      assert(!page1Html.contains(s"user_id=${loggedInUser.userId}, sync_date=$firstDate"))
      assert(page1Html.contains("""href="/job/status/followed-artists-sync?status=FAILED&amp;page=2">Next</a>"""))

      assert(status(page2Result) == OK)
      assert(page2Html.contains("全 101 件 / Page 2 / 2"))
      assert(page2Html.contains("""value="FAILED" checked"""))
      assert(page2Html.contains(s"user_id=${loggedInUser.userId}, sync_date=$firstDate"))
      assert(!page2Html.contains(s"user_id=${loggedInUser.userId}, sync_date=$scheduledDate"))
      assert(page2Html.contains("""href="/job/status/followed-artists-sync?status=FAILED&amp;page=1">Previous</a>"""))
      assert(page2Html.contains("""<span class="job-status-page-link is-current">2</span>"""))
    }

    Scenario("各対象ジョブの queue 詳細を表示する") {
      val loggedInUser = writeLoggedInUserSession()
      val written = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val authorizationId = writeAuthorization(loggedInUser.userId)
        authorizationRefreshQueueWriter.write(
          authorizationRefreshQueueRow(
            authorizationId = authorizationId,
            status = QueueJobStatus.Processing
          )
        )

        writeFollowedArtist(loggedInUser.userId, "spotify-artist-code")
        artistReleaseSyncQueueWriter.write(
          artistReleaseQueueRow(
            spotifyArtistCode = "spotify-artist-code",
            status = QueueJobStatus.Blocked,
            lastErrorType = "Forbidden"
          )
        )

        val notificationTarget = writeNotificationTarget(loggedInUser.userId)
        notificationQueueWriter.write(
          notificationQueueRow(
            userNewReleaseEventId = notificationTarget.userNewReleaseEventId,
            playlistSettingId = notificationTarget.playlistSettingId,
            status = QueueJobStatus.Succeeded
          )
        )

        WrittenJobTargets(
          authorizationId = authorizationId,
          notificationTarget = notificationTarget
        )
      }

      val refreshHtml = contentAsString(
        route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/spotify-access-token-refresh")).value
      )
      val artistHtml = contentAsString(
        route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/artist-releases-sync")).value
      )
      val notificationHtml = contentAsString(
        route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/user-new-release-notification-delivery")).value
      )

      assert(refreshHtml.contains("""<h1>Spotify access token refresh</h1>"""))
      assert(refreshHtml.contains(s"authorization_id=${written.authorizationId}"))
      assertStatusBadge(refreshHtml, "PROCESSING")

      assert(artistHtml.contains("""<h1>Artist releases sync</h1>"""))
      assert(artistHtml.contains("spotify_artist_code=spotify-artist-code, sync_scope=INCREMENTAL"))
      assertStatusBadge(artistHtml, "BLOCKED")

      assert(notificationHtml.contains("""<h1>User new release notification delivery</h1>"""))
      assert(notificationHtml.contains(s"user_new_release_event_id=${written.notificationTarget.userNewReleaseEventId}"))
      assert(notificationHtml.contains(s"playlist_setting_id=${written.notificationTarget.playlistSettingId}"))
      assertStatusBadge(notificationHtml, "SUCCEEDED")
    }

    Scenario("followed-artists-sync は削除済みまたは無効ユーザーに紐づく queue を表示・集計しない") {
      val loggedInUser = writeLoggedInUserSession()
      val activeSyncDate = LocalDate.of(2026, 7, 8)
      val disabledSyncDate = LocalDate.of(2026, 7, 7)
      val deletedSyncDate = LocalDate.of(2026, 7, 6)
      val (disabledUserId, deletedUserId) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val disabledUserId = writeUser("disabled-followed-artists-user", enabled = 0L)
        val deletedUserId = writeUser("deleted-followed-artists-user", deleted = 1L)
        followedArtistSyncQueueWriter.write(followedArtistsQueueRow(loggedInUser.userId, QueueJobStatus.Scheduled, activeSyncDate))
        followedArtistSyncQueueWriter.write(
          followedArtistsQueueRow(disabledUserId, QueueJobStatus.Failed, disabledSyncDate, lastErrorType = "DisabledUser")
        )
        followedArtistSyncQueueWriter.write(
          followedArtistsQueueRow(deletedUserId, QueueJobStatus.Blocked, deletedSyncDate, lastErrorType = "DeletedUser")
        )
        (disabledUserId, deletedUserId)
      }

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/followed-artists-sync")).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assertSummaryHasNoCountColumn(html)
      assert(html.contains(targetLabelCell(s"user_id=${loggedInUser.userId}, sync_date=$activeSyncDate")))
      assert(!html.contains(targetLabelCell(s"user_id=$disabledUserId, sync_date=$disabledSyncDate")))
      assert(!html.contains(targetLabelCell(s"user_id=$deletedUserId, sync_date=$deletedSyncDate")))
      assert(!html.contains("DisabledUser"))
      assert(!html.contains("DeletedUser"))
    }

    Scenario("spotify-access-token-refresh は削除済み authorization や削除済みまたは無効ユーザーの queue を表示・集計しない") {
      val loggedInUser = writeLoggedInUserSession()
      val (activeAuthorizationId, deletedAuthorizationId, deletedUserAuthorizationId, disabledUserAuthorizationId) =
        databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
          val activeAuthorizationId = writeAuthorization(loggedInUser.userId)
          val deletedAuthorizationUserId = writeUser("deleted-authorization-owner")
          val deletedAuthorizationId = writeAuthorization(deletedAuthorizationUserId, deleted = 1L)
          val deletedUserId = writeUser("deleted-refresh-user", deleted = 1L)
          val deletedUserAuthorizationId = writeAuthorization(deletedUserId)
          val disabledUserId = writeUser("disabled-refresh-user", enabled = 0L)
          val disabledUserAuthorizationId = writeAuthorization(disabledUserId)
          authorizationRefreshQueueWriter.write(
            authorizationRefreshQueueRow(activeAuthorizationId, QueueJobStatus.Scheduled)
          )
          authorizationRefreshQueueWriter.write(
            authorizationRefreshQueueRow(deletedAuthorizationId, QueueJobStatus.Failed)
          )
          authorizationRefreshQueueWriter.write(
            authorizationRefreshQueueRow(deletedUserAuthorizationId, QueueJobStatus.Blocked)
          )
          authorizationRefreshQueueWriter.write(
            authorizationRefreshQueueRow(disabledUserAuthorizationId, QueueJobStatus.Processing)
          )
          (activeAuthorizationId, deletedAuthorizationId, deletedUserAuthorizationId, disabledUserAuthorizationId)
        }

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/spotify-access-token-refresh")).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assertSummaryHasNoCountColumn(html)
      assert(html.contains(targetLabelCell(s"authorization_id=$activeAuthorizationId")))
      assert(!html.contains(targetLabelCell(s"authorization_id=$deletedAuthorizationId")))
      assert(!html.contains(targetLabelCell(s"authorization_id=$deletedUserAuthorizationId")))
      assert(!html.contains(targetLabelCell(s"authorization_id=$disabledUserAuthorizationId")))
    }

    Scenario("user-new-release-notification-delivery は無効な親データに紐づく queue を表示・集計しない") {
      val loggedInUser = writeLoggedInUserSession()
      val (activeTarget, excludedTargets) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val activeTarget = writeNotificationTarget(loggedInUser.userId, suffix = "notification-active")
        val deletedEventUserId = writeUser("deleted-event-notification-user")
        val deletedEventTarget = writeNotificationTarget(deletedEventUserId, suffix = "notification-deleted-event", eventDeleted = 1L)
        val deletedUserId = writeUser("deleted-notification-user", deleted = 1L)
        val deletedUserTarget = writeNotificationTarget(deletedUserId, suffix = "notification-deleted-user")
        val disabledUserId = writeUser("disabled-notification-user", enabled = 0L)
        val disabledUserTarget = writeNotificationTarget(disabledUserId, suffix = "notification-disabled-user")
        val deletedPlaylistSettingUserId = writeUser("deleted-playlist-setting-user")
        val deletedPlaylistSettingTarget = writeNotificationTarget(
          deletedPlaylistSettingUserId,
          suffix = "notification-deleted-playlist-setting",
          playlistDeleted = 1L
        )
        val disabledPlaylistSettingUserId = writeUser("disabled-playlist-setting-user")
        val disabledPlaylistSettingTarget = writeNotificationTarget(
          disabledPlaylistSettingUserId,
          suffix = "notification-disabled-playlist-setting",
          playlistEnabled = 0L
        )
        notificationQueueWriter.write(
          notificationQueueRow(activeTarget.userNewReleaseEventId, activeTarget.playlistSettingId, QueueJobStatus.Succeeded)
        )
        notificationQueueWriter.write(
          notificationQueueRow(deletedEventTarget.userNewReleaseEventId, deletedEventTarget.playlistSettingId, QueueJobStatus.Scheduled)
        )
        notificationQueueWriter.write(
          notificationQueueRow(deletedUserTarget.userNewReleaseEventId, deletedUserTarget.playlistSettingId, QueueJobStatus.Failed)
        )
        notificationQueueWriter.write(
          notificationQueueRow(disabledUserTarget.userNewReleaseEventId, disabledUserTarget.playlistSettingId, QueueJobStatus.Blocked)
        )
        notificationQueueWriter.write(
          notificationQueueRow(
            deletedPlaylistSettingTarget.userNewReleaseEventId,
            deletedPlaylistSettingTarget.playlistSettingId,
            QueueJobStatus.Processing
          )
        )
        notificationQueueWriter.write(
          notificationQueueRow(
            disabledPlaylistSettingTarget.userNewReleaseEventId,
            disabledPlaylistSettingTarget.playlistSettingId,
            QueueJobStatus.Skipped
          )
        )
        (
          activeTarget,
          Seq(deletedEventTarget, deletedUserTarget, disabledUserTarget, deletedPlaylistSettingTarget, disabledPlaylistSettingTarget)
        )
      }

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/user-new-release-notification-delivery")).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assertSummaryHasNoCountColumn(html)
      assert(html.contains(targetLabelCell(notificationTargetLabel(activeTarget))))
      excludedTargets.foreach { target =>
        assert(!html.contains(targetLabelCell(notificationTargetLabel(target))))
      }
    }

    Scenario("artist-releases-sync は active user の followed artist に紐づかない queue を表示・集計しない") {
      val loggedInUser = writeLoggedInUserSession()
      val activeArtistCode = "job-status-active-followed-artist"
      val orphanArtistCode = "job-status-orphan-artist"
      val disabledUserArtistCode = "job-status-disabled-user-artist"
      val deletedUserArtistCode = "job-status-deleted-user-artist"

      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        writeFollowedArtist(loggedInUser.userId, activeArtistCode)
        val disabledUserId = writeUser("disabled-artist-release-user", enabled = 0L)
        writeFollowedArtist(disabledUserId, disabledUserArtistCode)
        val deletedUserId = writeUser("deleted-artist-release-user", deleted = 1L)
        writeFollowedArtist(deletedUserId, deletedUserArtistCode)
        artistReleaseSyncQueueWriter.write(
          artistReleaseQueueRow(activeArtistCode, QueueJobStatus.Scheduled, lastErrorType = "")
        )
        artistReleaseSyncQueueWriter.write(
          artistReleaseQueueRow(orphanArtistCode, QueueJobStatus.Failed, lastErrorType = "NoActiveFollower")
        )
        artistReleaseSyncQueueWriter.write(
          artistReleaseQueueRow(disabledUserArtistCode, QueueJobStatus.Blocked, lastErrorType = "DisabledFollower")
        )
        artistReleaseSyncQueueWriter.write(
          artistReleaseQueueRow(deletedUserArtistCode, QueueJobStatus.Processing, lastErrorType = "DeletedFollower")
        )
      }

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/artist-releases-sync")).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assertSummaryHasNoCountColumn(html)
      assert(html.contains(targetLabelCell(s"spotify_artist_code=$activeArtistCode, sync_scope=INCREMENTAL")))
      assert(!html.contains(targetLabelCell(s"spotify_artist_code=$orphanArtistCode, sync_scope=INCREMENTAL")))
      assert(!html.contains(targetLabelCell(s"spotify_artist_code=$disabledUserArtistCode, sync_scope=INCREMENTAL")))
      assert(!html.contains(targetLabelCell(s"spotify_artist_code=$deletedUserArtistCode, sync_scope=INCREMENTAL")))
      assert(!html.contains("NoActiveFollower"))
      assert(!html.contains("DisabledFollower"))
      assert(!html.contains("DeletedFollower"))
    }

    Scenario("user-new-release-events-sync はイベント履歴を表示し、無効な親データを除外する") {
      val loggedInUser = writeLoggedInUserSession()
      val (activeTarget, eventWithoutNotificationTarget, excludedTargets) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val activeTarget = writeUserNewReleaseEventTarget(loggedInUser.userId, suffix = "event-active")
        val playlistSettingId = writePlaylistSetting(loggedInUser.userId, suffix = "event-active")
        notificationQueueWriter.write(
          notificationQueueRow(
            activeTarget.eventId,
            playlistSettingId,
            QueueJobStatus.Failed,
            lastErrorType = "RateLimited"
          )
        )
        val eventWithoutNotificationTarget = writeUserNewReleaseEventTarget(loggedInUser.userId, suffix = "event-without-notification")
        val otherSyncTarget = writeUserNewReleaseEventTarget(
          loggedInUser.userId,
          suffix = "event-other-sync",
          detectionSyncCode = "other-sync"
        )
        val deletedEventTarget = writeUserNewReleaseEventTarget(loggedInUser.userId, suffix = "event-deleted", eventDeleted = 1L)
        val deletedUserId = writeUser("deleted-events-sync-user", deleted = 1L)
        val deletedUserTarget = writeUserNewReleaseEventTarget(deletedUserId, suffix = "event-deleted-user")
        val disabledUserId = writeUser("disabled-events-sync-user", enabled = 0L)
        val disabledUserTarget = writeUserNewReleaseEventTarget(disabledUserId, suffix = "event-disabled-user")
        val deletedArtistReleaseTarget = writeUserNewReleaseEventTarget(
          loggedInUser.userId,
          suffix = "event-deleted-artist-release",
          artistReleaseDeleted = 1L
        )

        (
          activeTarget,
          eventWithoutNotificationTarget,
          Seq(otherSyncTarget, deletedEventTarget, deletedUserTarget, disabledUserTarget, deletedArtistReleaseTarget)
        )
      }

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/user-new-release-events-sync")).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assert(html.contains("""<h1>User new release events sync</h1>"""))
      assert(html.contains("""<nav class="job-status-breadcrumb" aria-label="Breadcrumb">"""))
      assert(html.contains("""<li><a href="/job/status">Job status</a></li>"""))
      assert(html.contains("""<li aria-current="page">User new release events sync</li>"""))
      assert(!html.contains("""<form class="job-status-filter""""))
      assert(!html.contains("""<table class="job-status-summary-table" aria-label="Event summary">"""))
      assertSummaryHasNoCountColumn(html)
      assert(html.contains(s"""<td class="job-status-number">${activeTarget.eventId}</td>"""))
      assert(html.contains(s"""<td class="job-status-number">${activeTarget.userId}</td>"""))
      assert(html.contains(s"""<td class="job-status-number">${activeTarget.artistReleaseId}</td>"""))
      assert(html.contains(s"""<td class="job-status-target">${activeTarget.spotifyReleaseCode}</td>"""))
      assert(html.contains(s"""<td class="job-status-target">${activeTarget.sourceSpotifyArtistCode}</td>"""))
      assert(html.contains("""<th scope="col">notification queue</th>"""))
      assert(html.contains("""<th scope="col">next attempt</th>"""))
      assert(html.contains("""<th scope="col">error</th>"""))
      assertStatusBadge(html, "FAILED")
      assert(html.contains("RateLimited"))
      assert(html.contains(fixedNow.toString))
      assert(html.contains(s"""<td class="job-status-target">${eventWithoutNotificationTarget.spotifyReleaseCode}</td>"""))
      assertEventNotificationQueueIsEmpty(html, eventWithoutNotificationTarget)
      assert(html.contains("全 2 件 / Page 1 / 1"))
      excludedTargets.foreach { target =>
        assert(!html.contains(target.spotifyReleaseCode))
        assert(!html.contains(target.sourceSpotifyArtistCode))
      }
    }

    Scenario("user-new-release-events-sync は event 詳細をページングする") {
      val loggedInUser = writeLoggedInUserSession()
      val targets = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        (0L to 100L).map { dayOffset =>
          writeUserNewReleaseEventTarget(
            userId = loggedInUser.userId,
            suffix = s"event-page-$dayOffset",
            detectedAt = fixedNow.toLocalDateTime.plusDays(dayOffset)
          )
        }
      }
      val oldestTarget = targets.head
      val newestTarget = targets.last

      val page1Result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/user-new-release-events-sync")).value
      val page2Result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/user-new-release-events-sync?page=2")).value
      val page1Html = contentAsString(page1Result)
      val page2Html = contentAsString(page2Result)

      assert(status(page1Result) == OK)
      assert(page1Html.contains("全 101 件 / Page 1 / 2"))
      assert(page1Html.contains(newestTarget.spotifyReleaseCode))
      assert(!page1Html.contains(oldestTarget.spotifyReleaseCode))
      assert(page1Html.contains("""href="/job/status/user-new-release-events-sync?page=2">Next</a>"""))

      assert(status(page2Result) == OK)
      assert(page2Html.contains("全 101 件 / Page 2 / 2"))
      assert(page2Html.contains(oldestTarget.spotifyReleaseCode))
      assert(!page2Html.contains(newestTarget.spotifyReleaseCode))
      assert(page2Html.contains("""href="/job/status/user-new-release-events-sync?page=1">Previous</a>"""))
      assert(page2Html.contains("""<span class="job-status-page-link is-current">2</span>"""))
    }

    Scenario("未知の status は Bad Request を返す") {
      val loggedInUser = writeLoggedInUserSession()

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/followed-artists-sync?status=UNKNOWN")).value
      val html = contentAsString(result)

      assert(status(result) == BAD_REQUEST)
      assert(html.contains("パラメーター status が不正です"))
    }

    Scenario("user-new-release-events-sync は status query を受け付けない") {
      val loggedInUser = writeLoggedInUserSession()

      val result = route(
        app,
        loggedInGetRequest(loggedInUser.sessionToken, "/job/status/user-new-release-events-sync?status=SUCCEEDED")
      ).value
      val html = contentAsString(result)

      assert(status(result) == BAD_REQUEST)
      assert(html.contains("パラメーター status が不正です"))
    }

    Scenario("未知の page は Bad Request を返す") {
      val loggedInUser = writeLoggedInUserSession()

      Seq("0", "abc", "").foreach { page =>
        val result = route(app, loggedInGetRequest(loggedInUser.sessionToken, s"/job/status/followed-artists-sync?page=$page")).value
        val html = contentAsString(result)

        assert(status(result) == BAD_REQUEST)
        assert(html.contains("パラメーター page が不正です"))
      }

      val multiValueResult = route(app, loggedInGetRequest(loggedInUser.sessionToken, "/job/status/followed-artists-sync?page=1&page=2")).value
      val multiValueHtml = contentAsString(multiValueResult)

      assert(status(multiValueResult) == BAD_REQUEST)
      assert(multiValueHtml.contains("パラメーター page が不正です"))
    }

    Scenario("未知の job-name は Not Found を返す") {
      val result = route(app, FakeRequest(GET, "/job/status/unknown-job").withHeaders(HOST -> "localhost:9000")).value
      val html = contentAsString(result)

      assert(status(result) == NOT_FOUND)
      assert(html.contains("404 Not Found"))
      assert(html.contains("/job/status/unknown-job"))
    }
  }

  private def writeLoggedInUserSession(): LoggedInUser =
    databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
      val userId = userWriter.write(UserRow)
      val sessionTokenService = new SessionTokenService(testApplicationConfig, dateTimeService)
      val issuedSessionToken = sessionTokenService.issue(userId)
      userSessionTokenWriter.write(userSessionTokenRow(userId, issuedSessionToken))
      LoggedInUser(userId, issuedSessionToken.value)
    }

  private def writeUser(
      userName: String,
      enabled: Long = 1L,
      deleted: Long = 0L
  )(using DBSession): Long =
    userWriter.write(
      UserRow.copy(
        userName = userName,
        displayName = s"display $userName",
        enabled = enabled,
        deletedAt = dbDeletedAt(deleted),
        deletedUser = dbDeletedUser(deleted),
        deleted = deleted
      )
    )

  private def writeFollowedArtist(
      userId: Long,
      spotifyArtistCode: String
  )(using DBSession): Unit = {
    followedArtistWriter.write(
      followedArtistRow(userId).copy(
        spotifyArtistCode = spotifyArtistCode,
        artistName = s"Artist $spotifyArtistCode"
      )
    )
    ()
  }

  private def writeAuthorization(userId: Long, deleted: Long = 0L)(using DBSession): Long = {
    authorizationWriter.write(
      UserSpotifyAuthorizationSource(
        userId = userId,
        scopeText = "playlist-modify-private playlist-read-private",
        accessTokenCipher = Array[Byte](1),
        accessTokenNonce = Array[Byte](2),
        accessTokenTag = Array[Byte](3),
        refreshTokenCipher = Array[Byte](4),
        refreshTokenNonce = Array[Byte](5),
        refreshTokenTag = Array[Byte](6),
        encryptionAlgorithm = "AES/GCM/NoPadding",
        encryptionKeyVersion = "test-key",
        tokenType = "Bearer",
        accessTokenExpiresAt = fixedNow.plus(1.hour),
        refreshMarginSeconds = 300,
        lastAuthorizedAt = Some(fixedNow),
        lastRefreshedAt = None,
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = sourceDeletedAt(deleted),
        createdUser = AuditUser.User(userId),
        updatedUser = AuditUser.User(userId),
        deletedUser = sourceDeletedUser(deleted),
        deleted = deleted,
        lockVersion = 0L
      ).toUserSpotifyAuthorizationDbRow
    )

    sql"""
      select id
      from user_spotify_authorization
      where user_id = {userId}
      order by id desc
      limit 1
    """
      .bindByName("userId" -> userId)
      .map(_.long("id"))
      .single
      .apply()
      .value
  }

  private def writeNotificationTarget(
      userId: Long,
      suffix: String = "default",
      eventDeleted: Long = 0L,
      playlistEnabled: Long = 1L,
      playlistDeleted: Long = 0L
  )(using DBSession): NotificationTarget = {
    val releaseCode =
      if (suffix == "default") JulySecondReleaseRow.spotifyReleaseCode else s"${JulySecondReleaseRow.spotifyReleaseCode}-$suffix"
    val sourceArtistCode =
      if (suffix == "default") JulySecondReleaseRow.sourceSpotifyArtistCode else s"${JulySecondReleaseRow.sourceSpotifyArtistCode}-$suffix"
    val artistReleaseId = artistReleasesWriter.write(
      JulySecondReleaseRow.copy(
        spotifyReleaseCode = releaseCode,
        sourceSpotifyArtistCode = sourceArtistCode,
        releaseName =
          if (suffix == "default") JulySecondReleaseRow.releaseName else s"${JulySecondReleaseRow.releaseName} $suffix"
      )
    )
    val userNewReleaseEventId = userNewReleaseEventsWriter
      .writeIfAbsentReturningId(
        julySecondEventRow(userId, artistReleaseId).copy(
          spotifyReleaseCode = releaseCode,
          sourceSpotifyArtistCode = sourceArtistCode,
          detectionSyncCode = if (suffix == "default") "test-sync" else s"test-sync-$suffix",
          deletedAt = dbDeletedAt(eventDeleted),
          deletedUser = dbDeletedUser(eventDeleted),
          deleted = eventDeleted
        )
      )
      .value
    val playlistSettingId = writePlaylistSetting(userId, suffix, playlistEnabled, playlistDeleted)
    NotificationTarget(userNewReleaseEventId, playlistSettingId)
  }

  private def writePlaylistSetting(
      userId: Long,
      suffix: String,
      enabled: Long = 1L,
      deleted: Long = 0L
  )(using DBSession): Long = {
    val playlistCode =
      if (suffix == "default") "playlist-code" else s"playlist-code-$suffix"
    playlistSettingWriter.write(
      UserPlaylistSettingSource(
        userId = userId,
        playlistUsageType = PlaylistUsageType.NewReleaseNotification,
        spotifyPlaylistCode = playlistCode,
        spotifyPlaylistUri = s"spotify:playlist:$playlistCode",
        playlistName = if (suffix == "default") "Dachshund Radar" else s"Dachshund Radar $suffix",
        enabled = enabled,
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = sourceDeletedAt(deleted),
        createdUser = AuditUser.User(userId),
        updatedUser = AuditUser.User(userId),
        deletedUser = sourceDeletedUser(deleted),
        deleted = deleted,
        lockVersion = 0L
      ).toUserPlaylistSettingDbRow
    )
  }

  private def writeUserNewReleaseEventTarget(
      userId: Long,
      suffix: String,
      detectionSyncCode: String = UserNewReleaseEventsSyncDetectionSyncCode,
      detectedAt: LocalDateTime = fixedNow.toLocalDateTime,
      eventDeleted: Long = 0L,
      artistReleaseDeleted: Long = 0L
  )(using DBSession): UserNewReleaseEventTarget = {
    val releaseCode = s"${JulySecondReleaseRow.spotifyReleaseCode}-$suffix"
    val sourceArtistCode = s"${JulySecondReleaseRow.sourceSpotifyArtistCode}-$suffix"
    val artistReleaseId = artistReleasesWriter.write(
      JulySecondReleaseRow.copy(
        spotifyReleaseCode = releaseCode,
        sourceSpotifyArtistCode = sourceArtistCode,
        releaseName = s"${JulySecondReleaseRow.releaseName} $suffix",
        deletedAt = dbDeletedAt(artistReleaseDeleted),
        deletedUser = dbDeletedUser(artistReleaseDeleted),
        deleted = artistReleaseDeleted
      )
    )
    val userNewReleaseEventId = userNewReleaseEventsWriter
      .writeIfAbsentReturningId(
        julySecondEventRow(userId, artistReleaseId).copy(
          spotifyReleaseCode = releaseCode,
          sourceSpotifyArtistCode = sourceArtistCode,
          detectedAt = detectedAt,
          detectionSyncCode = detectionSyncCode,
          deletedAt = dbDeletedAt(eventDeleted),
          deletedUser = dbDeletedUser(eventDeleted),
          deleted = eventDeleted
        )
      )
      .value

    UserNewReleaseEventTarget(
      eventId = userNewReleaseEventId,
      userId = userId,
      artistReleaseId = artistReleaseId,
      spotifyReleaseCode = releaseCode,
      sourceSpotifyArtistCode = sourceArtistCode
    )
  }

  private def authorizationRefreshQueueRow(
      authorizationId: Long,
      status: QueueJobStatus
  ) =
    UserSpotifyAuthorizationRefreshQueueSource(
      authorizationId = authorizationId,
      status = status,
      nextAttemptAt = Some(fixedNow),
      attemptCount = 2,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = if (status == QueueJobStatus.Processing) "lock-token" else "",
      lockedUntil = if (status == QueueJobStatus.Processing) Some(fixedNow.plus(1.hour)) else None,
      lastAttemptedAt = Some(fixedNow),
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

  private def followedArtistsQueueRow(
      userId: Long,
      status: QueueJobStatus,
      syncDate: LocalDate,
      lastErrorType: String = "",
      deleted: Long = 0L
  ) =
    FollowedArtistSyncQueueSource(
      userId = userId,
      syncDate = syncDate,
      status = status,
      requestedLimit = 50,
      afterCursor = None,
      nextAttemptAt = Some(fixedNow),
      lastAttemptedAt = Some(fixedNow),
      completedAt = if (status == QueueJobStatus.Succeeded) Some(fixedNow) else None,
      attemptCount = 1,
      lastFailedAt = if (lastErrorType.nonEmpty) Some(fixedNow) else None,
      lastErrorType = lastErrorType,
      lockToken = if (status == QueueJobStatus.Processing) "lock-token" else "",
      lockedUntil = if (status == QueueJobStatus.Processing) Some(fixedNow.plus(1.hour)) else None,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = if (deleted == 0L) None else Some(fixedNow),
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = if (deleted == 0L) AuditUser.Empty else AuditUser.System,
      deleted = deleted,
      lockVersion = 0L
    ).toFollowedArtistSyncQueueDbRow

  private def artistReleaseQueueRow(
      spotifyArtistCode: String,
      status: QueueJobStatus,
      lastErrorType: String
  ) =
    ArtistReleaseSyncQueueSource(
      spotifyArtistCode = spotifyArtistCode,
      syncScope = "INCREMENTAL",
      status = status,
      includeGroups = "album,single",
      market = None,
      requestedLimit = 10,
      nextOffset = 0,
      nextAttemptAt = Some(fixedNow),
      lastAttemptedAt = Some(fixedNow),
      completedAt = None,
      attemptCount = 3,
      lastFailedAt = Some(fixedNow),
      lastErrorType = lastErrorType,
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

  private def notificationQueueRow(
      userNewReleaseEventId: Long,
      playlistSettingId: Long,
      status: QueueJobStatus,
      lastErrorType: String = ""
  ) =
    UserNewReleaseNotificationQueueSource(
      userNewReleaseEventId = userNewReleaseEventId,
      releaseNotificationType = ReleaseNotificationType.Playlist,
      playlistSettingId = playlistSettingId,
      status = status,
      nextAttemptAt = Some(fixedNow),
      attemptCount = 1,
      lastFailedAt = if (lastErrorType.nonEmpty) Some(fixedNow) else None,
      lastErrorType = lastErrorType,
      lockToken = "",
      lockedUntil = None,
      lastAttemptedAt = Some(fixedNow),
      completedAt = Some(fixedNow),
      spotifySnapshotId = "snapshot-id",
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserNewReleaseNotificationQueueDbRow

  private def loggedInGetRequest(sessionToken: String, path: String) =
    FakeRequest(GET, path)
      .withHeaders(HOST -> "localhost:9000")
      .withCookies(Cookie(testApplicationConfig.cookie.session.name, sessionToken))

  private def assertSummaryHasNoCountColumn(html: String): Unit = {
    assert(!html.contains("""<th scope="col">count</th>"""))
    assert(!html.contains("job-status-summary-count"))
  }

  private def assertQueueStatusSummaryIsHidden(html: String): Unit =
    assert(!html.contains("""<table class="job-status-summary-table" aria-label="Status summary">"""))

  private def assertStatusBadge(html: String, status: String): Unit = {
    val pattern =
      s"""(?s)<span class="job-status-badge">\\s*<span>$status</span>\\s*</span>""".r

    assert(pattern.findFirstIn(html).nonEmpty, s"status badge for $status was not found")
  }

  private def assertEventNotificationQueueIsEmpty(html: String, target: UserNewReleaseEventTarget): Unit = {
    val pattern =
      s"""(?s)<td class="job-status-number">${target.eventId}</td>.*?<td class="job-status-target">${target.sourceSpotifyArtistCode}</td>\\s*<td class="job-status-number">-</td>\\s*<td>\\s*-\\s*</td>\\s*<td class="job-status-number">-</td>""".r

    assert(pattern.findFirstIn(html).nonEmpty, s"empty notification queue cells for event ${target.eventId} were not found")
  }

  private def targetLabelCell(targetLabel: String): String =
    s"""<td class="job-status-target">$targetLabel</td>"""

  private def notificationTargetLabel(target: NotificationTarget): String =
    s"user_new_release_event_id=${target.userNewReleaseEventId}, " +
      s"release_notification_type=${ReleaseNotificationType.Playlist.dbValue}, " +
      s"playlist_setting_id=${target.playlistSettingId}"

  private def sourceDeletedAt(deleted: Long) =
    if (deleted == 0L) None else Some(fixedNow)

  private def dbDeletedAt(deleted: Long) =
    if (deleted == 0L) None else Some(fixedNow.toLocalDateTime)

  private def sourceDeletedUser(deleted: Long): AuditUser =
    if (deleted == 0L) AuditUser.Empty else AuditUser.System

  private def dbDeletedUser(deleted: Long): String =
    sourceDeletedUser(deleted).dbValue

  private final case class LoggedInUser(
      userId: Long,
      sessionToken: String
  )

  private final case class NotificationTarget(
      userNewReleaseEventId: Long,
      playlistSettingId: Long
  )

  private final case class UserNewReleaseEventTarget(
      eventId: Long,
      userId: Long,
      artistReleaseId: Long,
      spotifyReleaseCode: String,
      sourceSpotifyArtistCode: String
  )

  private final case class WrittenJobTargets(
      authorizationId: Long,
      notificationTarget: NotificationTarget
  )
}
