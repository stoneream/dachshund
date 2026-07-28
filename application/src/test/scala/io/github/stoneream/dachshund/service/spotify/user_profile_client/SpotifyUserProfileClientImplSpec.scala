package io.github.stoneream.dachshund.service.spotify.user_profile_client

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.stoneream.dachshund.config.spotify.SpotifyRequestPolicyConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestThrottler
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class SpotifyUserProfileClientImplSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-user-profile-client-impl-spec")
  private given ExecutionContext = ExecutionContext.global

  Feature("Spotify user profile request policy") {
    Scenario("profile API の429でも共通 throttler を停止する") {
      withServer { apiBaseUrl =>
        val base = TestApplicationConfig()
        val config = base.copy(
          spotify = base.spotify.copy(
            client = base.spotify.client.copy(
              apiBaseUrl = apiBaseUrl,
              requestPolicy = SpotifyRequestPolicyConfig(
                pacingInterval = Duration.Zero,
                rateLimitFallbackDelay = 30.seconds
              )
            )
          )
        )
        val throttler = new SpotifyRequestThrottler(config)
        val client = new SpotifyUserProfileClientImpl(config, ioDispatcher, throttler)

        val failure = client
          .getCurrentUserProfile("token")
          .failed
          .futureValue
          .asInstanceOf[SpotifyUserProfileClientException.ProfileFetchFailed]
        val throttled = throttler.acquirePermit().futureValue.left.toOption.get

        assert(failure.statusCode == 429)
        assert(failure.errorCode.contains("429"))
        assert(throttled.retryAfter > 9.seconds)
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 3.seconds, interval = 10.millis)

  private def withServer(test: String => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/v1/me",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getResponseHeaders.add("Retry-After", "10")
          val bytes = """{"error":{"status":429,"message":"too many requests"}}"""
            .getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(429, bytes.length)
          val responseBodyStream = exchange.getResponseBody
          try responseBodyStream.write(bytes)
          finally responseBodyStream.close()
        }
      }
    )
    server.start()
    try {
      test(s"http://127.0.0.1:${server.getAddress.getPort}/v1")
    } finally {
      server.stop(0)
    }
  }

  private object ioDispatcher extends IoDispatcher {
    override def execute(runnable: Runnable): Unit =
      ExecutionContext.global.execute(runnable)

    override def reportFailure(cause: Throwable): Unit =
      ExecutionContext.global.reportFailure(cause)
  }
}
