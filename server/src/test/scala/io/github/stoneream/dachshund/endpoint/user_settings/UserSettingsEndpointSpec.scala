package io.github.stoneream.dachshund.endpoint.user_settings

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.endpoint.home.HomeEndpointFixture.*
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingSource
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyUserWriter, UserPlaylistSettingWriter, UserSessionTokenWriter}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.PlaylistUsageType
import io.github.stoneream.dachshund.module.{ApplicationModule, DatabaseInitializer}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyArtistReleaseSummaryPage}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model.SpotifyFollowedArtistsPage
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.model.{SpotifyAddItemsToPlaylistResult, SpotifyCreatePlaylistResult, SpotifyPlaylist, SpotifyPlaylistPage}
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientImpl}
import io.github.stoneream.dachshund.test.lib.PlayApplicationDatabaseSupport
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

import scala.concurrent.Future

class UserSettingsEndpointSpec extends AnyFeatureSpec with PlayApplicationDatabaseSupport with OptionValues with IdiomaticMockito {
  private val productionHost = "dachshund.yap-yap-dog.net"
  private val productionOrigin = s"https://$productionHost"

  private val dateTimeService = mock[DateTimeService]
  dateTimeService.now() returns fixedNow

  private val accessTokenProvider = new RecordingAccessTokenProvider
  private val spotifyClient = new RecordingSpotifyClient

  override protected lazy val app: Application = GuiceApplicationBuilder()
    .disable[ApplicationModule]
    .overrides(
      bind[ApplicationConfig].toInstance(testApplicationConfig),
      bind[DateTimeService].toInstance(dateTimeService),
      bind[SpotifyAuthorizationCodeAccessTokenProvider].toInstance(accessTokenProvider),
      bind[SpotifyClient].toInstance(spotifyClient),
      bind[SpotifyOAuthClient].to[SpotifyOAuthClientImpl],
      bind[SpotifyUserProfileClient].to[SpotifyUserProfileClientImpl],
      bind[DatabaseInitializer].toSelf.eagerly()
    )
    .build()

  private val userWriter = new SpotifyUserWriter
  private val userSessionTokenWriter = new UserSessionTokenWriter
  private val playlistSettingWriter = new UserPlaylistSettingWriter

  Feature("User settings endpoint") {
    Scenario("未ログインの場合は Spotify ログインへ redirect する") {
      resetFakes()

      val result = route(app, FakeRequest(GET, "/user-settings").withHeaders(HOST -> "localhost:9000")).value

      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).value == "/spotify/auth/login")
    }

    Scenario("ログインユーザーには管理 playlist 設定フォームを表示する") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()

      val result = route(app, loggedInGetRequest(loggedInUser.sessionToken)).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assert(html.contains("""<h1>User settings</h1>"""))
      assert(html.contains("display user"))
      assert(html.contains("""<form class="settings-form" method="post" action="/user-settings">"""))
      assert(html.contains("""name="newReleasePlaylistEnabled""""))
      assert(!newReleasePlaylistCheckboxChecked(html))
      assert(html.contains("Dachshund Radar"))
      assert(!html.contains("""name="spotifyPlaylistInput""""))
      assert(!html.contains("""name="playlistName""""))
      assert(!html.contains("Spotify playlist URL / ID"))
      assert(!html.contains("Playlist name"))
    }

    Scenario("未設定で有効保存すると Dachshund Radar playlist を作成して保存する") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()

      val postResult = route(
        app,
        loggedInPostRequest(
          loggedInUser.sessionToken,
          "newReleasePlaylistEnabled" -> "true"
        )
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(redirectLocation(postResult).value == "/user-settings")
      assert(accessTokenProvider.inputs.map(_.userId) == Vector(loggedInUser.userId))
      assert(spotifyClient.playlistPageCalls == Vector(("current-access-token", 50, 0)))
      assert(spotifyClient.createPlaylistCalls == Vector(("current-access-token", "Dachshund Radar", false)))

      val setting = findPlaylistSetting(loggedInUser.userId).value
      assert(setting.spotifyPlaylistCode == "playlist-1")
      assert(setting.spotifyPlaylistUri == "spotify:playlist:playlist-1")
      assert(setting.playlistName == "Dachshund Radar")
      assert(setting.enabled == 1L)
    }

    Scenario("未設定でチェックを外して保存しても設定行を作らない") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()

      val postResult = route(
        app,
        loggedInPostRequest(loggedInUser.sessionToken)
      ).value

      val showResult = route(app, loggedInGetRequest(loggedInUser.sessionToken)).value

      assert(status(postResult) == SEE_OTHER)
      assert(redirectLocation(postResult).value == "/user-settings")
      assert(findPlaylistSetting(loggedInUser.userId).isEmpty)
      assert(accessTokenProvider.inputs.isEmpty)
      assert(spotifyClient.playlistPageCalls.isEmpty)
      assert(spotifyClient.createPlaylistCalls.isEmpty)
      assert(!newReleasePlaylistCheckboxChecked(contentAsString(showResult)))
    }

    Scenario("本番 origin の POST を受け付ける") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()

      val postResult = route(
        app,
        loggedInProductionPostRequest(
          loggedInUser.sessionToken,
          "newReleasePlaylistEnabled" -> "true"
        )
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(redirectLocation(postResult).value == "/user-settings")
      assert(findPlaylistSetting(loggedInUser.userId).value.enabled == 1L)
    }

    Scenario("Spotify API が forbidden を返した場合は再認可へ redirect する") {
      resetFakes()
      spotifyClient.playlistPageFailure = Some(SpotifyClientException.Forbidden(new RuntimeException("forbidden")))
      val loggedInUser = writeLoggedInUserSession()

      val postResult = route(
        app,
        loggedInPostRequest(
          loggedInUser.sessionToken,
          "newReleasePlaylistEnabled" -> "true"
        )
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(redirectLocation(postResult).value == "/spotify/auth/login")
      assert(findPlaylistSetting(loggedInUser.userId).isEmpty)
      assert(spotifyClient.createPlaylistCalls.isEmpty)
    }

    Scenario("同名 playlist が存在する場合は UUID suffix 付きの playlist を作成する") {
      resetFakes()
      spotifyClient.playlistPages = Vector(
        SpotifyPlaylistPage(
          playlists = Seq(SpotifyPlaylist("existing-playlist", "Dachshund Radar", "spotify:playlist:existing-playlist")),
          nextOffset = None
        )
      )
      val loggedInUser = writeLoggedInUserSession()

      val postResult = route(
        app,
        loggedInPostRequest(
          loggedInUser.sessionToken,
          "newReleasePlaylistEnabled" -> "true"
        )
      ).value

      assert(status(postResult) == SEE_OTHER)
      val createdName = spotifyClient.createPlaylistCalls.head._2
      assert(createdName.matches("""Dachshund Radar_[0-9a-fA-F-]{36}"""))
      assert(findPlaylistSetting(loggedInUser.userId).value.playlistName == createdName)
    }

    Scenario("並行作成で設定保存が競合した場合は作成済み playlist を cleanup する") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()
      spotifyClient.beforeCreatePlaylistResult = () => writePlaylistSetting(loggedInUser.userId, enabled = 1L)

      val postResult = route(
        app,
        loggedInPostRequest(
          loggedInUser.sessionToken,
          "newReleasePlaylistEnabled" -> "true"
        )
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(redirectLocation(postResult).value == "/user-settings")
      assert(spotifyClient.createPlaylistCalls == Vector(("current-access-token", "Dachshund Radar", false)))
      assert(spotifyClient.unfollowPlaylistCalls == Vector(("current-access-token", "playlist-1")))
      val setting = findPlaylistSetting(loggedInUser.userId).value
      assert(setting.spotifyPlaylistCode == "stored-playlist")
      assert(setting.enabled == 1L)
    }

    Scenario("既に有効な設定がある場合は Spotify API を呼ばずに保存成功にする") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()
      writePlaylistSetting(loggedInUser.userId, enabled = 1L)

      val postResult = route(
        app,
        loggedInPostRequest(
          loggedInUser.sessionToken,
          "newReleasePlaylistEnabled" -> "true"
        )
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(accessTokenProvider.inputs.isEmpty)
      assert(spotifyClient.playlistPageCalls.isEmpty)
      assert(spotifyClient.createPlaylistCalls.isEmpty)
      assert(findPlaylistSetting(loggedInUser.userId).value.enabled == 1L)
    }

    Scenario("チェックを外して保存すると既存設定を無効化する") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()
      writePlaylistSetting(loggedInUser.userId, enabled = 1L)

      val postResult = route(
        app,
        loggedInPostRequest(loggedInUser.sessionToken)
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(findPlaylistSetting(loggedInUser.userId).value.enabled == 0L)
    }
  }

  private def resetFakes(): Unit = {
    accessTokenProvider.reset()
    spotifyClient.reset()
  }

  private def writeLoggedInUserSession(): LoggedInUser =
    databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
      val userId = userWriter.write(UserRow)
      val sessionTokenService = new SessionTokenService(testApplicationConfig, dateTimeService)
      val issuedSessionToken = sessionTokenService.issue(userId)
      userSessionTokenWriter.write(userSessionTokenRow(userId, issuedSessionToken))
      LoggedInUser(userId, issuedSessionToken.value)
    }

  private def writePlaylistSetting(userId: Long, enabled: Long): Long =
    databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
      playlistSettingWriter.write(
        UserPlaylistSettingSource(
          userId = userId,
          playlistUsageType = PlaylistUsageType.NewReleaseNotification,
          spotifyPlaylistCode = "stored-playlist",
          spotifyPlaylistUri = "spotify:playlist:stored-playlist",
          playlistName = "Stored playlist",
          enabled = enabled,
          createdAt = fixedNow,
          updatedAt = fixedNow,
          deletedAt = None,
          createdUser = AuditUser.User(userId),
          updatedUser = AuditUser.User(userId),
          deletedUser = AuditUser.Empty,
          deleted = 0L,
          lockVersion = 0L
        ).toUserPlaylistSettingDbRow
      )
    }

  private def loggedInGetRequest(sessionToken: String, host: String = "localhost:9000") =
    FakeRequest(GET, "/user-settings")
      .withHeaders(HOST -> host)
      .withCookies(Cookie(testApplicationConfig.cookie.session.name, sessionToken))

  private def loggedInPostRequest(sessionToken: String, formValues: (String, String)*) =
    FakeRequest(POST, "/user-settings")
      .withHeaders(HOST -> "localhost:9000")
      .withCookies(Cookie(testApplicationConfig.cookie.session.name, sessionToken))
      .withFormUrlEncodedBody(formValues*)

  private def loggedInProductionPostRequest(sessionToken: String, formValues: (String, String)*) =
    FakeRequest(POST, "/user-settings")
      .withHeaders(HOST -> productionHost, ORIGIN -> productionOrigin)
      .withCookies(Cookie(testApplicationConfig.cookie.session.name, sessionToken))
      .withFormUrlEncodedBody(formValues*)

  private def newReleasePlaylistCheckboxChecked(html: String): Boolean =
    html.contains("""name="newReleasePlaylistEnabled" value="true" checked""")

  private def findPlaylistSetting(userId: Long): Option[PlaylistSettingRecord] =
    NamedDB(testApplicationConfig.db.master.connectionPoolName).readOnly { implicit session =>
      sql"""
        select
          spotify_playlist_code,
          spotify_playlist_uri,
          playlist_name,
          enabled
        from
          user_playlist_setting
        where
          user_id = {userId}
          and playlist_usage_type = {playlistUsageType}
          and deleted = 0
      """
        .bindByName(
          "userId" -> userId,
          "playlistUsageType" -> PlaylistUsageType.NewReleaseNotification.dbValue
        )
        .map { row =>
          PlaylistSettingRecord(
            spotifyPlaylistCode = row.string("spotify_playlist_code"),
            spotifyPlaylistUri = row.string("spotify_playlist_uri"),
            playlistName = row.string("playlist_name"),
            enabled = row.long("enabled")
          )
        }
        .single
        .apply()
    }

  private final case class LoggedInUser(
      userId: Long,
      sessionToken: String
  )

  private final case class PlaylistSettingRecord(
      spotifyPlaylistCode: String,
      spotifyPlaylistUri: String,
      playlistName: String,
      enabled: Long
  )

  private final class RecordingAccessTokenProvider extends SpotifyAuthorizationCodeAccessTokenProvider {
    var inputs: Vector[SpotifyAuthorizationCodeAccessTokenResolveInput] = Vector.empty

    def reset(): Unit =
      inputs = Vector.empty

    override def resolve(
        input: SpotifyAuthorizationCodeAccessTokenResolveInput
    )(using LoggingContext): Future[ResolvedSpotifyAuthorizationCodeAccessToken] = {
      inputs = inputs :+ input
      Future.successful(
        ResolvedSpotifyAuthorizationCodeAccessToken(
          accessToken = "current-access-token",
          tokenType = "Bearer",
          scopeText = "playlist-modify-private playlist-read-private",
          expiresAt = fixedNow
        )
      )
    }
  }

  private final class RecordingSpotifyClient extends SpotifyClient {
    var playlistPages: Vector[SpotifyPlaylistPage] = Vector(SpotifyPlaylistPage(Seq.empty, None))
    var playlistPageFailure: Option[Throwable] = None
    var playlistPageCalls: Vector[(String, Int, Int)] = Vector.empty
    var createPlaylistCalls: Vector[(String, String, Boolean)] = Vector.empty
    var unfollowPlaylistCalls: Vector[(String, String)] = Vector.empty
    var beforeCreatePlaylistResult: () => Unit = () => ()

    def reset(): Unit = {
      playlistPages = Vector(SpotifyPlaylistPage(Seq.empty, None))
      playlistPageFailure = None
      playlistPageCalls = Vector.empty
      createPlaylistCalls = Vector.empty
      unfollowPlaylistCalls = Vector.empty
      beforeCreatePlaylistResult = () => ()
    }

    override def getCurrentUserPlaylistPage(
        accessToken: String,
        limit: Int,
        offset: Int
    )(using LoggingContext): Future[SpotifyPlaylistPage] = {
      playlistPageCalls = playlistPageCalls :+ (accessToken, limit, offset)
      playlistPageFailure
        .map(Future.failed)
        .getOrElse(Future.successful(playlistPages.lift(playlistPageCalls.size - 1).getOrElse(playlistPages.last)))
    }

    override def createCurrentUserPlaylist(
        accessToken: String,
        playlistName: String,
        isPublic: Boolean
    )(using LoggingContext): Future[SpotifyCreatePlaylistResult] = {
      createPlaylistCalls = createPlaylistCalls :+ (accessToken, playlistName, isPublic)
      val playlistNumber = createPlaylistCalls.size
      beforeCreatePlaylistResult()
      Future.successful(
        SpotifyCreatePlaylistResult(
          spotifyPlaylistCode = s"playlist-$playlistNumber",
          playlistName = playlistName,
          spotifyPlaylistUri = s"spotify:playlist:playlist-$playlistNumber"
        )
      )
    }

    override def unfollowPlaylist(
        accessToken: String,
        spotifyPlaylistCode: String
    )(using LoggingContext): Future[Unit] = {
      unfollowPlaylistCalls = unfollowPlaylistCalls :+ (accessToken, spotifyPlaylistCode)
      Future.unit
    }

    override def getFollowedArtists(
        accessToken: String,
        afterCursor: Option[String],
        limit: Int
    )(using LoggingContext): Future[SpotifyFollowedArtistsPage] =
      Future.failed(new AssertionError("getFollowedArtists must not be called"))

    override def addItemsToPlaylist(
        accessToken: String,
        spotifyPlaylistCode: String,
        trackUris: Seq[String]
    )(using LoggingContext): Future[SpotifyAddItemsToPlaylistResult] =
      Future.failed(new AssertionError("addItemsToPlaylist must not be called"))

    override def getArtistReleaseSummaryPage(
        accessToken: String,
        spotifyArtistCode: String,
        includeGroups: String,
        market: Option[String],
        limit: Int,
        offset: Int
    )(using LoggingContext): Future[SpotifyArtistReleaseSummaryPage] =
      Future.failed(new AssertionError("getArtistReleaseSummaryPage must not be called"))

    override def getArtistRelease(
        accessToken: String,
        sourceSpotifyArtistCode: String,
        summary: SpotifyArtistReleaseSummary,
        market: Option[String]
    )(using LoggingContext): Future[SpotifyArtistRelease] =
      Future.failed(new AssertionError("getArtistRelease must not be called"))

  }
}
