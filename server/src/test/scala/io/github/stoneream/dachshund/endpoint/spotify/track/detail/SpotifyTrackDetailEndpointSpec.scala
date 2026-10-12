package io.github.stoneream.dachshund.endpoint.spotify.track.detail

import io.github.stoneream.dachshund.service.reccobeats.{ReccoBeatsClient, ReccoBeatsClientException}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.endpoint.home.HomeEndpointFixture.*
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyUserWriter, UserSessionTokenWriter}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.module.{ApplicationModule, DatabaseInitializer}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderException as TokenException, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_track.model.SpotifyTrackDetail
import io.github.stoneream.dachshund.service.reccobeats.model.ReccoBeatsAudioFeatures
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientImpl}
import io.github.stoneream.dachshund.test.lib.PlayApplicationDatabaseSupport
import org.mockito.ArgumentMatchers.{any, anyString, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.OptionValues
import org.scalatest.featurespec.AnyFeatureSpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Cookie}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future
import scala.concurrent.duration.*

class SpotifyTrackDetailEndpointSpec extends AnyFeatureSpec with PlayApplicationDatabaseSupport with OptionValues {
  private val dateTimeService = mock(classOf[DateTimeService])
  private val tokenProvider = mock(classOf[SpotifyAuthorizationCodeAccessTokenProvider])
  private val spotifyClient = mock(classOf[SpotifyClient])
  private val reccoBeatsClient = mock(classOf[ReccoBeatsClient])
  private val token = ResolvedSpotifyAuthorizationCodeAccessToken("user-token", "Bearer", "", fixedNow.plus(1.hour))
  private val trackId = "4uLU6hMCjMI75M1A2tKUQC"
  private val path = s"/spotify/track/detail/$trackId"
  private val track = SpotifyTrackDetail(trackId, "曲A", Seq("歌手A", "歌手B"), "アルバムA", Some("https://i.scdn.co/image/cover"), Some(180999), Some(42))
  private val features = ReccoBeatsAudioFeatures(Some(7), Some(0), Some(100.5), Some(0.625), Some(0.75), Some(0.5), Some(0), Some(1), Some(0.1), Some(0.2))

  override protected lazy val app: Application = GuiceApplicationBuilder()
    .disable[ApplicationModule]
    .overrides(
      bind[ApplicationConfig].toInstance(testApplicationConfig),
      bind[DateTimeService].toInstance(dateTimeService),
      bind[SpotifyOAuthClient].to[SpotifyOAuthClientImpl],
      bind[SpotifyUserProfileClient].to[SpotifyUserProfileClientImpl],
      bind[SpotifyAuthorizationCodeAccessTokenProvider].toInstance(tokenProvider),
      bind[SpotifyClient].toInstance(spotifyClient),
      bind[ReccoBeatsClient].toInstance(reccoBeatsClient),
      bind[DatabaseInitializer].toSelf.eagerly()
    )
    .build()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(tokenProvider, spotifyClient, reccoBeatsClient, dateTimeService)
    when(dateTimeService.now()).thenReturn(fixedNow)
    when(tokenProvider.resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext]))
      .thenReturn(Future.successful(token))
    when(spotifyClient.getTrack(anyString(), anyString())(using any[LoggingContext])).thenReturn(Future.successful(Some(track)))
    when(reccoBeatsClient.getTrackAudioFeatures(anyString())(using any[LoggingContext])).thenReturn(Future.successful(Some(features)))
  }

  Feature("ログインユーザーの楽曲詳細") {
    Scenario("未ログインでは Spotify を呼ばずログインへ redirect する") {
      val result = route(app, FakeRequest(GET, path).withHeaders(HOST -> "localhost:9000")).value
      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).contains("/spotify/auth/login"))
      verifyNoInteractions(tokenProvider, spotifyClient, reccoBeatsClient)
    }

    Scenario("不正な track_id は 400 とし Spotify を呼ばない") {
      val (_, session) = loggedInSession()
      Seq("short", "a" * 23, "!" * 22, "a" * 21 + "_").foreach { id =>
        val result = route(app, request(session, s"/spotify/track/detail/$id")).value
        assert(status(result) == BAD_REQUEST)
        assert(contentAsString(result).contains("パラメーター track_id が不正です"))
      }
      verifyNoInteractions(tokenProvider, spotifyClient, reccoBeatsClient)
    }

    Scenario("基本情報と 12 指標、検索への戻りリンクを表示する") {
      val (userId, session) = loggedInSession()
      val result = route(app, request(session)).value
      val html = contentAsString(result)
      assert(status(result) == OK)
      Seq("曲A", "歌手A, 歌手B", "アルバムA", "https://i.scdn.co/image/cover", "display user", "href=\"/spotify/search-track\"")
        .foreach(value => assert(html.contains(value)))
      assert(
        metrics(html) == Map(
          "Key" -> "G Minor",
          "Camelot" -> "6A",
          "BPM" -> "101",
          "Duration" -> "3:00",
          "Popularity" -> "42",
          "Energy" -> "63%",
          "Danceability" -> "75%",
          "Happiness" -> "50%",
          "Acousticness" -> "0%",
          "Instrumentalness" -> "100%",
          "Liveness" -> "10%",
          "Speechiness" -> "20%"
        )
      )
      verify(tokenProvider).resolve(eqTo(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, fixedNow)))(using any[LoggingContext])
      val calls = inOrder(spotifyClient, reccoBeatsClient)
      calls.verify(spotifyClient).getTrack(eqTo("user-token"), eqTo(trackId))(using any[LoggingContext])
      calls.verify(reccoBeatsClient).getTrackAudioFeatures(eqTo(trackId))(using any[LoggingContext])
    }

    Scenario("relink された曲の ID で音響指標を取得する") {
      val (_, session) = loggedInSession()
      val relinked = "7ouMYWpwJ422jRcDASZB7P"
      when(spotifyClient.getTrack(anyString(), anyString())(using any[LoggingContext]))
        .thenReturn(Future.successful(Some(track.copy(spotifyTrackCode = relinked))))
      assert(status(route(app, request(session)).value) == OK)
      verify(reccoBeatsClient).getTrackAudioFeatures(eqTo(relinked))(using any[LoggingContext])
    }

    Scenario("欠損指標を 0 にせずダッシュで表示する") {
      val (_, session) = loggedInSession()
      when(spotifyClient.getTrack(anyString(), anyString())(using any[LoggingContext])).thenReturn(Future.successful(Some(track.copy(popularity = None))))
      when(reccoBeatsClient.getTrackAudioFeatures(anyString())(using any[LoggingContext]))
        .thenReturn(Future.successful(Some(features.copy(key = Some(-1), tempo = None, energy = None))))
      val result = route(app, request(session)).value
      assert(status(result) == OK)
      val values = metrics(contentAsString(result))
      Seq("Key", "Camelot", "BPM", "Popularity", "Energy").foreach(label => assert(values(label) == "—"))
      assert(values("Acousticness") == "0%")
    }

    Scenario("ReccoBeats の 401・403・未登録は基本情報を残し Spotify の認可を更新しない") {
      val (loggedInUserId, session) = loggedInSession()
      val responses = Seq(
        Future.failed(ReccoBeatsClientException.AccessDenied(401)) -> "ReccoBeats によって音響指標へのアクセスが許可されていません",
        Future.failed(ReccoBeatsClientException.AccessDenied(403)) -> "ReccoBeats によって音響指標へのアクセスが許可されていません",
        Future.successful(None) -> "音響指標が見つかりませんでした"
      )
      responses.foreach { (response, message) =>
        when(reccoBeatsClient.getTrackAudioFeatures(anyString())(using any[LoggingContext])).thenReturn(response)
        val result = route(app, request(session)).value
        val html = contentAsString(result)
        assert(status(result) == OK)
        assert(html.contains("曲A"))
        assert(html.contains(message))
        assert(!html.contains("provider-secret"))
        val values = metrics(html)
        assert(values("Duration") == "3:00")
        assert(values("Popularity") == "42")
        assert(values.count(_._2 == "—") == 10)
      }
      verify(tokenProvider, times(3)).resolve(eqTo(SpotifyAuthorizationCodeAccessTokenResolveInput(loggedInUserId, fixedNow)))(using any[LoggingContext])
    }

    Scenario("楽曲の 404・403 はそれぞれのエラーを表示して音響指標を取得しない") {
      val (_, session) = loggedInSession()
      Seq(Future.successful(None) -> NOT_FOUND, Future.failed(SpotifyClientException.Forbidden(new RuntimeException("provider-secret"))) -> FORBIDDEN)
        .foreach { (response, expectedStatus) =>
          when(spotifyClient.getTrack(anyString(), anyString())(using any[LoggingContext])).thenReturn(response)
          val result = route(app, request(session)).value
          assert(status(result) == expectedStatus)
          assert(contentAsString(result).contains("role=\"alert\""))
          assert(!contentAsString(result).contains("provider-secret"))
        }
      verify(reccoBeatsClient, never()).getTrackAudioFeatures(anyString())(using any[LoggingContext])
    }

    Scenario("Spotify の 401 で一度だけ更新し、新しいトークンで楽曲を取得する") {
      val (userId, session) = loggedInSession()
      when(tokenProvider.resolve(eqTo(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, fixedNow, true)))(using any[LoggingContext]))
        .thenReturn(Future.successful(token.copy(accessToken = "refreshed-token")))
      when(spotifyClient.getTrack(eqTo("user-token"), anyString())(using any[LoggingContext]))
        .thenReturn(Future.failed(SpotifyClientException.Unauthorized(new RuntimeException("secret"))))
      val result = route(app, request(session)).value
      assert(status(result) == OK)
      verify(tokenProvider).resolve(eqTo(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, fixedNow, true)))(using any[LoggingContext])
      verify(tokenProvider, times(2)).resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext])
      verify(spotifyClient).getTrack(eqTo("refreshed-token"), eqTo(trackId))(using any[LoggingContext])
      verify(reccoBeatsClient).getTrackAudioFeatures(eqTo(trackId))(using any[LoggingContext])
    }

    Scenario("更新後も Spotify が 401 なら追加更新せず再認可へ進み ReccoBeats を呼ばない") {
      val (_, session) = loggedInSession()
      when(spotifyClient.getTrack(anyString(), anyString())(using any[LoggingContext]))
        .thenReturn(Future.failed(SpotifyClientException.Unauthorized(new RuntimeException("secret"))))
      val result = route(app, request(session)).value
      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).contains("/spotify/auth/login"))
      verify(tokenProvider, times(2)).resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext])
      verify(spotifyClient, times(2)).getTrack(anyString(), anyString())(using any[LoggingContext])
      verifyNoInteractions(reccoBeatsClient)
    }

    Scenario("認可情報不足・再認可要求は Spotify を呼ばずログインへ進む") {
      val (userId, session) = loggedInSession()
      Seq(TokenException.AuthorizationNotFound(userId), TokenException.ReauthorizationRequired(userId, "invalid_grant")).foreach { failure =>
        when(tokenProvider.resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext])).thenReturn(Future.failed(failure))
        val result = route(app, request(session)).value
        assert(status(result) == SEE_OTHER)
        assert(redirectLocation(result).contains("/spotify/auth/login"))
      }
      verifyNoInteractions(spotifyClient, reccoBeatsClient)
    }

    Seq(false, true).foreach { atAudioFeatures =>
      Scenario(s"${if (atAudioFeatures) "音響指標" else "楽曲"}の 429 では Retry-After の秒数と後で試す案内を表示する") {
        val (_, session) = loggedInSession()
        Seq(Some(500.millis) -> Some("1"), Some(1500.millis) -> Some("2"), None -> None).foreach { (delay, expectedHeader) =>
          val failure =
            if (atAudioFeatures) ReccoBeatsClientException.RateLimited(delay)
            else SpotifyClientException.RateLimited(delay, new RuntimeException("provider-secret"))
          stubFailure(failure, atAudioFeatures)
          val result = route(app, request(session)).value
          assert(status(result) == TOO_MANY_REQUESTS)
          assert(header("Retry-After", result) == expectedHeader)
          val html = contentAsString(result)
          assert(html.contains(expectedHeader.fold("時間をおいて再度お試しください。")(seconds => s"${seconds}秒後に再度お試しください。")))
          assert(!html.contains("provider-secret"))
        }
        verify(tokenProvider, times(3)).resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext])
      }

      Scenario(s"${if (atAudioFeatures) "音響指標" else "楽曲"}の通信障害・5xx・不正応答は 503 で再試行を案内する") {
        val (_, session) = loggedInSession()
        val failures =
          if (atAudioFeatures)
            Seq(
              ReccoBeatsClientException.Network(new RuntimeException("provider-secret")),
              ReccoBeatsClientException.HttpError(500),
              ReccoBeatsClientException.InvalidResponse(new RuntimeException("provider-secret"))
            )
          else
            Seq(
              SpotifyClientException.Network(new RuntimeException("provider-secret")),
              SpotifyClientException.ServerError(new RuntimeException("provider-secret")),
              SpotifyClientException.InvalidResponse(new RuntimeException("provider-secret"))
            )
        failures.foreach { failure =>
          stubFailure(failure, atAudioFeatures)
          val result = route(app, request(session)).value
          assert(status(result) == SERVICE_UNAVAILABLE)
          assert(contentAsString(result).contains("時間をおいて再度お試しください。"))
          assert(!contentAsString(result).contains("provider-secret"))
          assert(!contentAsString(result).contains("user-token"))
        }
      }
    }

    Scenario("トークン取得の一時障害は 503 とし Spotify を呼ばない") {
      val (userId, session) = loggedInSession()
      when(tokenProvider.resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext]))
        .thenReturn(Future.failed(TokenException.TemporaryFailure(userId, "network", fixedNow.plus(1.minute))))
      val result = route(app, request(session)).value
      assert(status(result) == SERVICE_UNAVAILABLE)
      verifyNoInteractions(spotifyClient, reccoBeatsClient)
    }

    Scenario("Spotify の曲名などの表示文字列を HTML として解釈しない") {
      val (_, session) = loggedInSession()
      val text = "<script>alert(1)</script>\" &"
      when(spotifyClient.getTrack(anyString(), anyString())(using any[LoggingContext]))
        .thenReturn(Future.successful(Some(track.copy(trackName = text, artistNames = Seq(text), albumName = text))))
      val result = route(app, request(session)).value
      val html = contentAsString(result)
      assert(status(result) == OK)
      assert(!html.contains("<script>alert(1)</script>"))
      assert(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }
  }

  private def stubFailure(failure: Throwable, atAudioFeatures: Boolean): Unit =
    if (atAudioFeatures)
      when(reccoBeatsClient.getTrackAudioFeatures(anyString())(using any[LoggingContext])).thenReturn(Future.failed(failure)): Unit
    else
      when(spotifyClient.getTrack(anyString(), anyString())(using any[LoggingContext])).thenReturn(Future.failed(failure)): Unit

  private def metrics(html: String): Map[String, String] =
    "<dt>([^<]+)</dt>\\s*<dd>([^<]+)</dd>".r.findAllMatchIn(html).map(m => m.group(1) -> m.group(2)).toMap

  private def loggedInSession(): (Long, String) =
    databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
      val userId = new SpotifyUserWriter().write(UserRow)
      val issued = new SessionTokenService(testApplicationConfig, dateTimeService).issue(userId)
      new UserSessionTokenWriter().write(userSessionTokenRow(userId, issued))
      (userId, issued.value)
    }

  private def request(session: String, uri: String = path): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, uri).withHeaders(HOST -> "localhost:9000").withCookies(Cookie(testApplicationConfig.cookie.session.name, session))
}
