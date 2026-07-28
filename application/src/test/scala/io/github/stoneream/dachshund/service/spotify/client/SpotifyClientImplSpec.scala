package io.github.stoneream.dachshund.service.spotify.client

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.stoneream.dachshund.config.spotify.SpotifyRequestPolicyConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.SpotifyArtistReleasesApi
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.SpotifyFollowedArtistsApi
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.SpotifyPlaylistApi
import io.github.stoneream.dachshund.service.spotify.client.lib.{SpotifyRequestExecutor, SpotifyRequestThrottler}
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class SpotifyClientImplSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-client-impl-spec")

  Feature("Spotify Web API rate limit response") {
    Scenario("Retry-After がない通常の 429 では fallback delay を使い、process-local throttler を停止する") {
      withServer(
        statusCode = 429,
        responseBody = """{"error":{"status":429,"message":"too many requests"}}"""
      ) { (baseUrl, requestCount) =>
        val client = spotifyClient(baseUrl, rateLimitFallbackDelay = 45.seconds)

        val firstFailure = client.getCurrentUserPlaylistPage("token", 10, 0).failed.futureValue
        val secondFailure = client.getCurrentUserPlaylistPage("token", 10, 0).failed.futureValue

        assert(firstFailure.asInstanceOf[SpotifyClientException.RateLimited].retryAfter.contains(45.seconds))
        assert(secondFailure.isInstanceOf[SpotifyClientException.RateLimited])
        assert(requestCount.get() == 1)
      }
    }

    Scenario("通常の 429 では長い Retry-After を上限なしで使う") {
      withServer(
        statusCode = 429,
        responseBody = """{"error":{"status":429,"message":"too many requests"}}""",
        headers = Map("Retry-After" -> "3600")
      ) { (baseUrl, _) =>
        val failure = spotifyClient(baseUrl)
          .getCurrentUserPlaylistPage("token", 10, 0)
          .failed
          .futureValue
          .asInstanceOf[SpotifyClientException.RateLimited]

        assert(failure.retryAfter.contains(1.hour))
      }
    }

    Scenario("SDK経由の429でも Retry-After を使い、後続リクエストを停止する") {
      withServer(
        statusCode = 429,
        responseBody = """{"error":{"status":429,"message":"too many requests"}}""",
        headers = Map("Retry-After" -> "10"),
        path = "/v1/me/following"
      ) { (baseUrl, requestCount) =>
        val client = spotifyClient(baseUrl)

        val firstFailure = client.getFollowedArtists("token", None, 10).failed.futureValue
        val secondFailure = client.getFollowedArtists("token", None, 10).failed.futureValue

        assert(firstFailure.asInstanceOf[SpotifyClientException.RateLimited].retryAfter.contains(10.seconds))
        assert(secondFailure.isInstanceOf[SpotifyClientException.RateLimited])
        assert(requestCount.get() == 1)
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 3.seconds, interval = 10.millis)

  private def spotifyClient(
      apiBaseUrl: String,
      rateLimitFallbackDelay: FiniteDuration = 30.seconds
  ): SpotifyClient = {
    val base = TestApplicationConfig()
    val config = base.copy(
      spotify = base.spotify.copy(
        client = base.spotify.client.copy(
          apiBaseUrl = apiBaseUrl,
          requestPolicy = SpotifyRequestPolicyConfig(
            pacingInterval = Duration.Zero,
            rateLimitFallbackDelay = rateLimitFallbackDelay
          )
        )
      )
    )
    val throttler = new SpotifyRequestThrottler(config)
    val requestExecutor = new SpotifyRequestExecutor(config, ioDispatcher, throttler)
    new SpotifyClientImpl(
      followedArtistsApi = new SpotifyFollowedArtistsApi(requestExecutor, ioDispatcher),
      artistReleasesApi = new SpotifyArtistReleasesApi(requestExecutor, ioDispatcher),
      playlistApi = new SpotifyPlaylistApi(config, requestExecutor, ioDispatcher)
    )
  }

  private def withServer(
      statusCode: Int,
      responseBody: String,
      headers: Map[String, String] = Map.empty,
      path: String = "/v1/me/playlists"
  )(
      test: (String, AtomicInteger) => Unit
  ): Unit = {
    val requestCount = new AtomicInteger(0)
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      path,
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          requestCount.incrementAndGet()
          headers.foreach { case (name, value) => exchange.getResponseHeaders.add(name, value) }
          val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(statusCode, bytes.length)
          val responseBodyStream = exchange.getResponseBody
          try responseBodyStream.write(bytes)
          finally responseBodyStream.close()
        }
      }
    )
    server.start()
    try {
      test(s"http://127.0.0.1:${server.getAddress.getPort}/v1", requestCount)
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
