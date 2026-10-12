package io.github.stoneream.dachshund.endpoint.spotify.search_track

import ch.qos.logback.classic.{Logger as LogbackLogger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.endpoint.home.HomeEndpointFixture.*
import io.github.stoneream.dachshund.filter.CustomLoggingFilter
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyUserWriter, UserSessionTokenWriter}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.module.{ApplicationModule, DatabaseInitializer}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderException as TokenException, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model.{SpotifyTrackSearchItem, SpotifyTrackSearchPage}
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientImpl}
import io.github.stoneream.dachshund.test.lib.PlayApplicationDatabaseSupport
import org.mockito.ArgumentMatchers.{any, anyInt, anyString, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.OptionValues
import org.scalatest.featurespec.AnyFeatureSpec
import org.slf4j.LoggerFactory
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Cookie}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import java.net.{URI, URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class SpotifySearchTrackEndpointSpec extends AnyFeatureSpec with PlayApplicationDatabaseSupport with OptionValues {
  private val dateTimeService = mock(classOf[DateTimeService])
  private val tokenProvider = mock(classOf[SpotifyAuthorizationCodeAccessTokenProvider])
  private val spotifyClient = mock(classOf[SpotifyClient])
  private val token = ResolvedSpotifyAuthorizationCodeAccessToken("user-token", "Bearer", "", fixedNow.plus(1.hour))
  private val path = "/spotify/search-track"

  override protected lazy val app: Application = GuiceApplicationBuilder()
    .disable[ApplicationModule]
    .overrides(
      bind[ApplicationConfig].toInstance(testApplicationConfig),
      bind[DateTimeService].toInstance(dateTimeService),
      bind[SpotifyOAuthClient].to[SpotifyOAuthClientImpl],
      bind[SpotifyUserProfileClient].to[SpotifyUserProfileClientImpl],
      bind[SpotifyAuthorizationCodeAccessTokenProvider].toInstance(tokenProvider),
      bind[SpotifyClient].toInstance(spotifyClient),
      bind[DatabaseInitializer].toSelf.eagerly()
    )
    .build()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(tokenProvider, spotifyClient, dateTimeService)
    when(dateTimeService.now()).thenReturn(fixedNow)
    when(tokenProvider.resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext]))
      .thenReturn(Future.successful(token))
  }

  Feature("ログインユーザーの楽曲検索") {
    Scenario("未ログインでは検索せずログインへ redirect する") {
      val result = route(app, FakeRequest(GET, s"$path?q=test").withHeaders(HOST -> "localhost:9000")).value
      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).contains("/spotify/auth/login"))
      verifyNoInteractions(tokenProvider, spotifyClient)
    }

    Scenario("初期表示と空白のみの検索は API を呼ばずフォームを表示する") {
      val (_, session) = loggedInSession()
      Seq(None, Some(""), Some(" \t　 ")).foreach { query =>
        val result = route(app, request(session, query)).value
        val html = contentAsString(result)
        assert(status(result) == OK)
        assert(html.contains("display user"))
        assert(!html.contains("該当する楽曲はありません。"))
      }
      verifyNoInteractions(tokenProvider, spotifyClient)
    }

    Scenario("前後の空白を除いた日本語と記号を検索し一覧と次ページに反映する") {
      val (userId, session) = loggedInSession()
      val query = "夜 & artist:星 + #"
      stubPage(
        SpotifyTrackSearchPage(
          Seq(
            SpotifyTrackSearchItem("one", "一曲目", Seq("歌手A", "歌手B"), "アルバムA", Some("https://i.scdn.co/image/cover")),
            SpotifyTrackSearchItem("two", "二曲目", Seq("歌手C"), "アルバムB", None)
          ),
          25,
          0,
          10,
          Some(10)
        )
      )
      val result = route(app, request(session, Some(s"  $query　"))).value
      val html = contentAsString(result)
      assert(status(result) == OK)
      assert(html.indexOf("一曲目") < html.indexOf("二曲目"))
      assert(html.contains("歌手A, 歌手B"))
      assert(html.contains("アルバムB"))
      assert(html.contains("https://i.scdn.co/image/cover"))
      assert(!html.contains("rel=\"prev\""))
      assert(linkQuery(html, "next") == Map("q" -> query, "offset" -> "10"))
      verify(tokenProvider).resolve(eqTo(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, fixedNow)))(using any[LoggingContext])
      verify(spotifyClient).searchTracks(eqTo("user-token"), eqTo(query), eqTo(10), eqTo(0))(using any[LoggingContext])
    }

    Scenario("検索結果の曲名から詳細へ進めるが ID のない楽曲はリンクにしない") {
      val (_, session) = loggedInSession()
      val trackId = "4uLU6hMCjMI75M1A2tKUQC"
      stubPage(
        SpotifyTrackSearchPage(
          Seq(
            SpotifyTrackSearchItem(trackId, "詳細あり", Seq.empty, "album", None),
            SpotifyTrackSearchItem("", "IDなし", Seq.empty, "album", None)
          ),
          2,
          0,
          10,
          None
        )
      )
      val result = route(app, request(session, Some("track"))).value
      val html = contentAsString(result)
      assert(status(result) == OK)
      val detailLink = s"""<a\\b[^>]*\\bhref="/spotify/track/detail/$trackId"[^>]*>\\s*詳細あり\\s*</a\\s*>""".r
      assert(detailLink.findFirstIn(html).nonEmpty)
      assert(html.contains("<span class=\"release-title\">IDなし</span>"))
    }

    Scenario("途中のページでは検索語を保って前後へ移動できる") {
      val (_, session) = loggedInSession()
      stubPage(SpotifyTrackSearchPage(Seq.empty, 35, 10, 10, Some(20)))
      val result = route(app, request(session, Some("track:夜"), Some("10"))).value
      val html = contentAsString(result)
      assert(status(result) == OK)
      assert(linkQuery(html, "prev") == Map("q" -> "track:夜", "offset" -> "0"))
      assert(linkQuery(html, "next") == Map("q" -> "track:夜", "offset" -> "20"))
      verify(spotifyClient).searchTracks(eqTo("user-token"), eqTo("track:夜"), eqTo(10), eqTo(10))(using any[LoggingContext])
      assert(!html.contains("name=\"offset\""))
    }

    Scenario("検索結果が空なら該当なしを表示する") {
      val (_, session) = loggedInSession()
      stubPage(SpotifyTrackSearchPage(Seq.empty, 0, 0, 10, None))
      val result = route(app, request(session, Some("test"))).value
      assert(status(result) == OK)
      assert(contentAsString(result).contains("該当する楽曲はありません。"))
      assert(!contentAsString(result).contains("検索結果のページ送り"))
    }

    Scenario("offset 1000 の終端ページは前へだけを表示する") {
      val (_, session) = loggedInSession()
      stubPage(SpotifyTrackSearchPage(Seq.empty, 3000, 1000, 10, None))
      val result = route(app, request(session, Some("test"), Some("1000"))).value
      assert(status(result) == OK)
      assert(linkQuery(contentAsString(result), "prev")("offset") == "990")
      assert(!contentAsString(result).contains("rel=\"next\""))
    }

    Scenario("offset の不正値は対象パラメーターを示す 400 とし API を呼ばない") {
      val (_, session) = loggedInSession()
      Seq("-1", "1001", "abc", "", "2147483648").foreach { offset =>
        val result = route(app, request(session, Some("test"), Some(offset))).value
        assert(status(result) == BAD_REQUEST)
        assert(contentAsString(result).contains("パラメーター offset が不正です"))
      }
      verifyNoInteractions(tokenProvider, spotifyClient)
    }

    Scenario("検索語と Spotify の表示文字列を HTML として解釈しない") {
      val (_, session) = loggedInSession()
      val text = "<script>alert(1)</script>\" &"
      stubPage(SpotifyTrackSearchPage(Seq(SpotifyTrackSearchItem("one", text, Seq(text), text, None)), 1, 0, 10, None))
      val result = route(app, request(session, Some(text))).value
      val html = contentAsString(result)
      assert(status(result) == OK)
      assert(!html.contains("<script>alert(1)</script>"))
      assert(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
      assert(html.contains("&quot;"))
    }

    Scenario("401 の場合はトークンを更新し新しいトークンで一度だけ再試行する") {
      val (userId, session) = loggedInSession()
      when(tokenProvider.resolve(eqTo(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, fixedNow, true)))(using any[LoggingContext]))
        .thenReturn(Future.successful(token.copy(accessToken = "refreshed-token")))
      when(spotifyClient.searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext]))
        .thenReturn(
          Future.failed(SpotifyClientException.Unauthorized(new RuntimeException("secret"))),
          Future.successful(SpotifyTrackSearchPage(Seq.empty, 0, 0, 10, None))
        )
      val result = route(app, request(session, Some("test"))).value
      assert(status(result) == OK)
      verify(tokenProvider).resolve(eqTo(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, fixedNow, true)))(using any[LoggingContext])
      verify(spotifyClient).searchTracks(eqTo("refreshed-token"), eqTo("test"), eqTo(10), eqTo(0))(using any[LoggingContext])
      verify(spotifyClient, times(2)).searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext])
    }

    Scenario("再試行も 401 なら無限再試行せずログインへ redirect する") {
      val (_, session) = loggedInSession()
      stubFailure(SpotifyClientException.Unauthorized(new RuntimeException("secret")))
      val result = route(app, request(session, Some("test"))).value
      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).contains("/spotify/auth/login"))
      verify(tokenProvider, times(2)).resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext])
      verify(spotifyClient, times(2)).searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext])
    }

    Scenario("認可情報不足と再認可要求は検索せずログインへ redirect する") {
      val (userId, session) = loggedInSession()
      Seq(TokenException.AuthorizationNotFound(userId), TokenException.ReauthorizationRequired(userId, "invalid_grant")).foreach { failure =>
        when(tokenProvider.resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext]))
          .thenReturn(Future.failed(failure))
        val result = route(app, request(session, Some("test"))).value
        assert(status(result) == SEE_OTHER)
        assert(redirectLocation(result).contains("/spotify/auth/login"))
      }
      verifyNoInteractions(spotifyClient)
    }

    Scenario("429 は検索語を保持し Retry-After と再試行案内を返す") {
      val (_, session) = loggedInSession()
      Seq(500.millis -> "1", 1500.millis -> "2").foreach { (delay, expectedHeader) =>
        stubFailure(SpotifyClientException.RateLimited(Some(delay), new RuntimeException("provider-secret")))
        val result = route(app, request(session, Some("  探したい曲　"))).value
        assert(status(result) == TOO_MANY_REQUESTS)
        assert(header("Retry-After", result).contains(expectedHeader))
        assert(contentAsString(result).contains("混み合っています"))
        assert(contentAsString(result).contains("value=\"探したい曲\""))
        assert(contentAsString(result).contains("display user"))
        assert(!contentAsString(result).contains("provider-secret"))
      }
      verify(spotifyClient, times(2)).searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext])
    }

    Scenario("待機時間のない 429 は Retry-After を付けず検索語とユーザー表示を保持する") {
      val (_, session) = loggedInSession()
      stubFailure(SpotifyClientException.RateLimited(None, new RuntimeException("provider-secret")))
      val result = route(app, request(session, Some("  探したい曲　"))).value
      assert(status(result) == TOO_MANY_REQUESTS)
      assert(header("Retry-After", result).isEmpty)
      assert(contentAsString(result).contains("混み合っています"))
      assert(contentAsString(result).contains("value=\"探したい曲\""))
      assert(contentAsString(result).contains("display user"))
      assert(!contentAsString(result).contains("provider-secret"))
      verify(spotifyClient).searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext])
    }

    Scenario("403 と通信障害では再認可せず検索失敗と検索語を表示する") {
      val (_, session) = loggedInSession()
      Seq(SpotifyClientException.Forbidden(new RuntimeException("provider-secret")), SpotifyClientException.Network(new RuntimeException("provider-secret")))
        .foreach { failure =>
          stubFailure(failure)
          val result = route(app, request(session, Some("  探したい曲　"))).value
          assert(status(result) == SERVICE_UNAVAILABLE)
          assert(contentAsString(result).contains("楽曲を検索できませんでした。"))
          assert(contentAsString(result).contains("value=\"探したい曲\""))
          assert(contentAsString(result).contains("display user"))
          assert(!contentAsString(result).contains("provider-secret"))
        }
      verify(spotifyClient, times(2)).searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext])
    }

    Scenario("一時的なトークン解決失敗は検索せず再試行案内を表示する") {
      val (userId, session) = loggedInSession()
      when(tokenProvider.resolve(any[SpotifyAuthorizationCodeAccessTokenResolveInput])(using any[LoggingContext]))
        .thenReturn(Future.failed(TokenException.TemporaryFailure(userId, "network", fixedNow.plus(1.minute))))
      val result = route(app, request(session, Some("test"))).value
      assert(status(result) == SERVICE_UNAVAILABLE)
      assert(contentAsString(result).contains("楽曲を検索できませんでした。"))
      verifyNoInteractions(spotifyClient)
    }

    Scenario("検索語を HTTP リクエストログへ出力しない") {
      val (_, session) = loggedInSession()
      stubPage(SpotifyTrackSearchPage(Seq.empty, 0, 0, 10, None))
      val logger = LoggerFactory.getLogger(app.injector.instanceOf[CustomLoggingFilter].getClass).asInstanceOf[LogbackLogger]
      val appender = new ListAppender[ILoggingEvent]
      appender.start()
      logger.addAppender(appender)
      try {
        val result = route(app, request(session, Some("private-search-query"))).value
        assert(status(result) == OK)
        val messages = appender.list.asScala.map(_.getFormattedMessage).mkString("\n")
        assert(messages.contains("q=%28redacted%29"))
        assert(!messages.contains("private-search-query"))
      } finally {
        logger.detachAppender(appender)
        appender.stop()
      }
    }
  }

  private def stubPage(page: SpotifyTrackSearchPage): Unit =
    when(spotifyClient.searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext]))
      .thenReturn(Future.successful(page)): Unit

  private def stubFailure(failure: Throwable): Unit =
    when(spotifyClient.searchTracks(anyString(), anyString(), anyInt(), anyInt())(using any[LoggingContext]))
      .thenReturn(Future.failed(failure)): Unit

  private def loggedInSession(): (Long, String) =
    databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
      val userId = new SpotifyUserWriter().write(UserRow)
      val issued = new SessionTokenService(testApplicationConfig, dateTimeService).issue(userId)
      new UserSessionTokenWriter().write(userSessionTokenRow(userId, issued))
      (userId, issued.value)
    }

  private def request(session: String, query: Option[String], offset: Option[String] = None): FakeRequest[AnyContentAsEmpty.type] = {
    val params = query.map("q" -> _).toSeq ++ offset.map("offset" -> _).toSeq
    val queryString = params.map { (key, value) => s"$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }.mkString("&")
    FakeRequest(GET, s"$path?$queryString")
      .withHeaders(HOST -> "localhost:9000")
      .withCookies(Cookie(testApplicationConfig.cookie.session.name, session))
  }

  private def linkQuery(html: String, rel: String): Map[String, String] = {
    val pattern = ("rel=\"" + rel + "\" href=\"([^\"]+)\"").r
    val url = pattern.findFirstMatchIn(html).value.group(1).replace("&amp;", "&")
    URI
      .create(url)
      .getRawQuery
      .split("&")
      .map { parameter =>
        val parts = parameter.split("=", 2)
        URLDecoder.decode(parts(0), StandardCharsets.UTF_8) -> URLDecoder.decode(parts(1), StandardCharsets.UTF_8)
      }
      .toMap
  }
}
