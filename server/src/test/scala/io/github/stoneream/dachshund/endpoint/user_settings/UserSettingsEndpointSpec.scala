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
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClient
import io.github.stoneream.dachshund.service.spotify.client.model.{SpotifyAddItemsToPlaylistResult, SpotifyArtistReleasePage, SpotifyCreatePlaylistResult, SpotifyFollowedArtistsPage, SpotifyPlaylist, SpotifyPlaylistPage}
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
      assert(html.contains("""checked"""))
      assert(html.contains("Dachshund Radar"))
      assert(!html.contains("""name="spotifyPlaylistInput""""))
      assert(!html.contains("""name="playlistName""""))
      assert(!html.contains("Spotify playlist URL / ID"))
      assert(!html.contains("Playlist name"))
    }

    Scenario("未設定で有効保存すると Dachshund Radar playlist を作成して保存する") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()
      val getResult = route(app, loggedInGetRequest(loggedInUser.sessionToken)).value
      val (csrfName, csrfValue) = csrfInput(contentAsString(getResult))
      val csrfCookie = cookies(getResult).get("csrfToken").value

      val postResult = route(
        app,
        FakeRequest(POST, "/user-settings")
          .withHeaders(HOST -> "localhost:9000")
          .withCookies(Cookie(testApplicationConfig.cookie.session.name, loggedInUser.sessionToken), csrfCookie)
          .withFormUrlEncodedBody(
            csrfName -> csrfValue,
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

    Scenario("本番 origin の POST を受け付ける") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()
      val getResult = route(app, loggedInGetRequest(loggedInUser.sessionToken, host = productionHost)).value
      val (csrfName, csrfValue) = csrfInput(contentAsString(getResult))
      val csrfCookie = cookies(getResult).get("csrfToken").value

      val postResult = route(
        app,
        FakeRequest(POST, "/user-settings")
          .withHeaders(HOST -> productionHost, ORIGIN -> productionOrigin)
          .withCookies(Cookie(testApplicationConfig.cookie.session.name, loggedInUser.sessionToken), csrfCookie)
          .withFormUrlEncodedBody(
            csrfName -> csrfValue,
            "newReleasePlaylistEnabled" -> "true"
          )
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(redirectLocation(postResult).value == "/user-settings")
      assert(findPlaylistSetting(loggedInUser.userId).value.enabled == 1L)
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
      val getResult = route(app, loggedInGetRequest(loggedInUser.sessionToken)).value
      val (csrfName, csrfValue) = csrfInput(contentAsString(getResult))
      val csrfCookie = cookies(getResult).get("csrfToken").value

      val postResult = route(
        app,
        FakeRequest(POST, "/user-settings")
          .withHeaders(HOST -> "localhost:9000")
          .withCookies(Cookie(testApplicationConfig.cookie.session.name, loggedInUser.sessionToken), csrfCookie)
          .withFormUrlEncodedBody(
            csrfName -> csrfValue,
            "newReleasePlaylistEnabled" -> "true"
          )
      ).value

      assert(status(postResult) == SEE_OTHER)
      val createdName = spotifyClient.createPlaylistCalls.head._2
      assert(createdName.matches("""Dachshund Radar_[0-9a-fA-F-]{36}"""))
      assert(findPlaylistSetting(loggedInUser.userId).value.playlistName == createdName)
    }

    Scenario("既に有効な設定がある場合は Spotify API を呼ばずに保存成功にする") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()
      writePlaylistSetting(loggedInUser.userId, enabled = 1L)
      val getResult = route(app, loggedInGetRequest(loggedInUser.sessionToken)).value
      val (csrfName, csrfValue) = csrfInput(contentAsString(getResult))
      val csrfCookie = cookies(getResult).get("csrfToken").value

      val postResult = route(
        app,
        FakeRequest(POST, "/user-settings")
          .withHeaders(HOST -> "localhost:9000")
          .withCookies(Cookie(testApplicationConfig.cookie.session.name, loggedInUser.sessionToken), csrfCookie)
          .withFormUrlEncodedBody(
            csrfName -> csrfValue,
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
      val getResult = route(app, loggedInGetRequest(loggedInUser.sessionToken)).value
      val (csrfName, csrfValue) = csrfInput(contentAsString(getResult))
      val csrfCookie = cookies(getResult).get("csrfToken").value

      val postResult = route(
        app,
        FakeRequest(POST, "/user-settings")
          .withHeaders(HOST -> "localhost:9000")
          .withCookies(Cookie(testApplicationConfig.cookie.session.name, loggedInUser.sessionToken), csrfCookie)
          .withFormUrlEncodedBody(csrfName -> csrfValue)
      ).value

      assert(status(postResult) == SEE_OTHER)
      assert(findPlaylistSetting(loggedInUser.userId).value.enabled == 0L)
    }

    Scenario("CSRF token がない POST は拒否する") {
      resetFakes()
      val loggedInUser = writeLoggedInUserSession()

      val result = route(
        app,
        FakeRequest(POST, "/user-settings")
          .withHeaders(HOST -> "localhost:9000")
          .withCookies(Cookie(testApplicationConfig.cookie.session.name, loggedInUser.sessionToken))
          .withFormUrlEncodedBody("newReleasePlaylistEnabled" -> "true")
      ).value

      assert(status(result) == FORBIDDEN)
      assert(findPlaylistSetting(loggedInUser.userId).isEmpty)
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

  private def csrfInput(html: String): (String, String) = {
    val Pattern = """<input type="hidden" name="([^"]+)" value="([^"]+)"\s*/?>""".r
    Pattern.findFirstMatchIn(html).map(result => result.group(1) -> result.group(2)).get
  }

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
    var playlistPageCalls: Vector[(String, Int, Int)] = Vector.empty
    var createPlaylistCalls: Vector[(String, String, Boolean)] = Vector.empty

    def reset(): Unit = {
      playlistPages = Vector(SpotifyPlaylistPage(Seq.empty, None))
      playlistPageCalls = Vector.empty
      createPlaylistCalls = Vector.empty
    }

    override def getCurrentUserPlaylistPage(
        accessToken: String,
        limit: Int,
        offset: Int
    )(using LoggingContext): Future[SpotifyPlaylistPage] = {
      playlistPageCalls = playlistPageCalls :+ (accessToken, limit, offset)
      Future.successful(playlistPages.lift(playlistPageCalls.size - 1).getOrElse(playlistPages.last))
    }

    override def createCurrentUserPlaylist(
        accessToken: String,
        playlistName: String,
        isPublic: Boolean
    )(using LoggingContext): Future[SpotifyCreatePlaylistResult] = {
      createPlaylistCalls = createPlaylistCalls :+ (accessToken, playlistName, isPublic)
      val playlistNumber = createPlaylistCalls.size
      Future.successful(
        SpotifyCreatePlaylistResult(
          spotifyPlaylistCode = s"playlist-$playlistNumber",
          playlistName = playlistName,
          spotifyPlaylistUri = s"spotify:playlist:playlist-$playlistNumber"
        )
      )
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

    override def getArtistReleasePage(
        accessToken: String,
        spotifyArtistCode: String,
        includeGroups: String,
        market: Option[String],
        limit: Int,
        offset: Int
    )(using loggingContext: LoggingContext): Future[SpotifyArtistReleasePage] =
      Future.failed(new AssertionError("getArtistReleasePage must not be called"))
  }
}
