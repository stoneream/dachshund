package io.github.stoneream.dachshund.service.spotify.client_credentials

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.*

class SpotifyClientCredentialsAccessTokenProviderSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-client-credentials-access-token-provider-spec")

  Feature("Spotify client credentials access token provider") {
    Scenario("有効期限内の token は Caffeine cache から返す") {
      val fixture = buildFixture()

      val first = fixture.provider.resolve(fixedNow).futureValue
      val second = fixture.provider.resolve(fixedNow.plus(100.seconds)).futureValue

      assert(first.accessToken == "access-token-1")
      assert(second.accessToken == "access-token-1")
      assert(fixture.oauthClient.requests == Vector(("spotify-client-id", "spotify-client-secret")))
    }

    Scenario("期限前更新範囲内に入った token は再取得する") {
      val fixture = buildFixture()
      fixture.oauthClient.responses = Vector(
        TokenResponse("access-token-1", "Bearer", 3600L, None, None),
        TokenResponse("access-token-2", "Bearer", 3600L, None, None)
      )

      val first = fixture.provider.resolve(fixedNow).futureValue
      val second = fixture.provider.resolve(fixedNow.plus(3400.seconds)).futureValue

      assert(first.accessToken == "access-token-1")
      assert(second.accessToken == "access-token-2")
      assert(fixture.oauthClient.requests == Vector(("spotify-client-id", "spotify-client-secret"), ("spotify-client-id", "spotify-client-secret")))
    }

    Scenario("forceRefresh の場合は cache を使わず再取得する") {
      val fixture = buildFixture()
      fixture.oauthClient.responses = Vector(
        TokenResponse("access-token-1", "Bearer", 3600L, None, None),
        TokenResponse("access-token-2", "Bearer", 3600L, None, None)
      )

      val first = fixture.provider.resolve(fixedNow).futureValue
      val second = fixture.provider.resolve(fixedNow.plus(100.seconds), forceRefresh = true).futureValue

      assert(first.accessToken == "access-token-1")
      assert(second.accessToken == "access-token-2")
      assert(fixture.oauthClient.requests == Vector(("spotify-client-id", "spotify-client-secret"), ("spotify-client-id", "spotify-client-secret")))
    }
  }

  private def buildFixture(): Fixture = {
    val oauthClient = new StubSpotifyOAuthClient
    val provider = new SpotifyClientCredentialsAccessTokenProviderImpl(
      applicationConfig = TestApplicationConfig(),
      spotifyOAuthClient = oauthClient,
      defaultExecutor = DirectExecutor
    )

    Fixture(provider, oauthClient)
  }

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  private final case class Fixture(
      provider: SpotifyClientCredentialsAccessTokenProvider,
      oauthClient: StubSpotifyOAuthClient
  )

  private class StubSpotifyOAuthClient extends SpotifyOAuthClient {
    var requests: Vector[(String, String)] = Vector.empty
    var responses: Vector[TokenResponse] =
      Vector(TokenResponse("access-token-1", "Bearer", 3600L, None, None))

    override def accessTokenRequest(
        code: String,
        redirectUri: String,
        clientId: String,
        clientSecret: String
    )(using LoggingContext): Future[TokenResponse] =
      Future.failed(new AssertionError("accessTokenRequest must not be called"))

    override def refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String
    )(using LoggingContext): Future[TokenResponse] =
      Future.failed(new AssertionError("refreshAccessToken must not be called"))

    override def requestClientCredentialsAccessToken(
        clientId: String,
        clientSecret: String
    )(using LoggingContext): Future[TokenResponse] = {
      requests = requests :+ ((clientId, clientSecret))
      val response = responses.headOption.getOrElse {
        throw new AssertionError("token response is not configured")
      }
      responses = responses.drop(1)
      Future.successful(response)
    }
  }

  private object DirectExecutor extends ExecutionContextExecutor with DefaultExecutor {
    override def execute(runnable: Runnable): Unit = runnable.run()

    override def reportFailure(cause: Throwable): Unit = throw cause
  }
}
