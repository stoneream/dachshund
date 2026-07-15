package io.github.stoneream.dachshund.endpoint.spotify.auth.callback

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.handler.lib.PageMeta
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.module.{ApplicationModule, DatabaseInitializer}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderImpl}
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientImpl}
import io.github.stoneream.dachshund.test.lib.PlayApplicationDatabaseSupport
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCase
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseInput
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseOutput
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseOutput.SpotifyAuthCallbackStatus
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.OptionValues
import org.scalatest.featurespec.AnyFeatureSpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class SpotifyAuthCallbackEndpointSpec extends AnyFeatureSpec with PlayApplicationDatabaseSupport with OptionValues with IdiomaticMockito {
  private val callbackUseCase = mock[SpotifyAuthCallbackUseCase]
  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")
  private val dateTimeService = mock[DateTimeService]
  dateTimeService.now() returns fixedNow

  override protected lazy val app: Application = GuiceApplicationBuilder()
    .disable[ApplicationModule]
    .overrides(
      bind[ApplicationConfig].toInstance(testApplicationConfig),
      bind[DateTimeService].toInstance(dateTimeService),
      bind[SpotifyAuthCallbackUseCase].toInstance(callbackUseCase),
      bind[SpotifyOAuthClient].to[SpotifyOAuthClientImpl],
      bind[SpotifyUserProfileClient].to[SpotifyUserProfileClientImpl],
      bind[SpotifyAuthorizationCodeAccessTokenProvider].to[SpotifyAuthorizationCodeAccessTokenProviderImpl],
      bind[DatabaseInitializer].toSelf.eagerly()
    )
    .build()

  Feature("Spotify auth callback endpoint") {
    Scenario("state query string がない場合は Bad Request を返す") {
      val result = route(
        app,
        FakeRequest(GET, "/spotify/auth/callback?code=authorization-code")
          .withHeaders(HOST -> "localhost:9000")
          .withCookies(Cookie(testApplicationConfig.cookie.externalAuthState.name, "cookie-state"))
      ).value
      val html = contentAsString(result)

      assert(status(result) == BAD_REQUEST)
      assert(html.contains("400 Bad Request"))
      assert(html.contains("パラメーター state が不正です"))
    }

    Scenario("external auth state cookie がない場合は Bad Request を返す") {
      val result = route(
        app,
        FakeRequest(GET, "/spotify/auth/callback?state=query-state&code=authorization-code")
          .withHeaders(HOST -> "localhost:9000")
      ).value
      val html = contentAsString(result)

      assert(status(result) == BAD_REQUEST)
      assert(html.contains("400 Bad Request"))
      assert(html.contains("パラメーター externalAuthState が不正です"))
    }

    Scenario("認可成功時は session cookie を発行してトップページへ redirect する") {
      callbackUseCase
        .run(*[SpotifyAuthCallbackUseCaseInput])(using *[LoggingContext])
        .returns(
          Future.successful(
            SpotifyAuthCallbackUseCaseOutput(
              status = SpotifyAuthCallbackStatus.AuthorizationReceived,
              userId = Some(123L),
              sessionToken = Some("session-token")
            )
          )
        )

      val result = route(app, validCallbackRequest()).value
      val sessionCookie = cookies(result).get(testApplicationConfig.cookie.session.name).value

      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).value == "/")
      assert(header(PageMeta.XRobotsTagHeaderName, result).value == PageMeta.NoIndexNoFollow)
      assert(sessionCookie.value == "session-token")
      assert(sessionCookie.path == "/")
      assert(sessionCookie.httpOnly)
      assert(sessionCookie.sameSite.contains(Cookie.SameSite.Lax))
      assert(cookies(result).get(testApplicationConfig.cookie.externalAuthState.name).exists(_.maxAge.contains(0)))
    }

    Scenario("認可成功扱いでも session token がない場合は cookie を発行しない") {
      reset(callbackUseCase)
      callbackUseCase
        .run(*[SpotifyAuthCallbackUseCaseInput])(using *[LoggingContext])
        .returns(
          Future.successful(
            SpotifyAuthCallbackUseCaseOutput(
              status = SpotifyAuthCallbackStatus.AuthorizationReceived,
              userId = Some(123L),
              sessionToken = None
            )
          )
        )

      val result = route(app, validCallbackRequest()).value

      assert(status(result) == INTERNAL_SERVER_ERROR)
      assert(cookies(result).get(testApplicationConfig.cookie.session.name).isEmpty)
      assert(cookies(result).get(testApplicationConfig.cookie.externalAuthState.name).exists(_.maxAge.contains(0)))
    }
  }

  private def validCallbackRequest() =
    FakeRequest(GET, "/spotify/auth/callback?state=query-state&code=authorization-code")
      .withHeaders(HOST -> "localhost:9000")
      .withCookies(Cookie(testApplicationConfig.cookie.externalAuthState.name, "query-state"))
}
