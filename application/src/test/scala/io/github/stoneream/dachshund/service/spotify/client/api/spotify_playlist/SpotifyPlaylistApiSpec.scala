package io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.circe.parser.parse
import io.github.stoneream.dachshund.config.spotify.SpotifyRequestPolicyConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException
import io.github.stoneream.dachshund.service.spotify.client.lib.{SpotifyRequestExecutor, SpotifyRequestThrottler}
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class SpotifyPlaylistApiSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-playlist-api-spec")

  Feature("Spotify playlist API") {
    Scenario("playlist page のlimitとoffsetを補正し、IDがないplaylistを除外する") {
      withServer { exchange =>
        assert(exchange.getRequestMethod == "GET")
        assert(exchange.getRequestURI.getRawQuery == "limit=50&offset=0")
        assert(exchange.getRequestHeaders.getFirst("Authorization") == "Bearer access-token")
        respond(
          exchange,
          200,
          """{
            |  "items": [
            |    {"id": "playlist-1", "name": "Playlist 1", "uri": "spotify:playlist:playlist-1"},
            |    {"id": null, "name": "Ignored", "uri": "spotify:playlist:ignored"}
            |  ],
            |  "next": "http://localhost/next",
            |  "limit": 50,
            |  "offset": 0
            |}""".stripMargin
        )
      } { baseUrl =>
        val result = playlistApi(baseUrl)
          .getCurrentUserPlaylistPage("access-token", limit = 100, offset = -1)
          .futureValue

        assert(result.playlists.map(_.spotifyPlaylistCode) == Seq("playlist-1"))
        assert(result.nextOffset.contains(50))
      }
    }

    Scenario("重複と空文字を除外し、100件単位で順番にplaylistへ追加する") {
      val requestCount = new AtomicInteger(0)
      withServer { exchange =>
        val currentRequest = requestCount.incrementAndGet()
        assert(exchange.getRequestMethod == "POST")
        assert(exchange.getRequestURI.getRawPath == "/v1/playlists/playlist%20code/items")
        val requestJson = parse(readBody(exchange)).toOption.get
        val uris = requestJson.hcursor.downField("uris").as[Seq[String]].toOption.get
        assert(uris.size == (if (currentRequest == 1) 100 else 1))
        respond(exchange, 201, s"""{"snapshot_id":"snapshot-$currentRequest"}""")
      } { baseUrl =>
        val trackUris = (1 to 101).map(index => s"spotify:track:$index") ++ Seq("", "spotify:track:1")
        val result = playlistApi(baseUrl)
          .addItemsToPlaylist("access-token", "playlist code", trackUris)
          .futureValue

        assert(result.spotifySnapshotId == "snapshot-2")
        assert(requestCount.get() == 2)
      }
    }

    Scenario("playlist codeをURL encodeしてunfollowする") {
      withServer { exchange =>
        assert(exchange.getRequestMethod == "DELETE")
        assert(exchange.getRequestURI.getRawPath == "/v1/playlists/playlist%2Fcode/followers")
        exchange.sendResponseHeaders(200, -1L)
        exchange.close()
      } { baseUrl =>
        playlistApi(baseUrl)
          .unfollowPlaylist("access-token", "playlist/code")
          .futureValue
      }
    }

    Scenario("空のtrack URIだけではリクエストせずInvalidResponseを返す") {
      val failure = playlistApi("http://127.0.0.1:1/v1")
        .addItemsToPlaylist("access-token", "playlist", Seq("", "  "))
        .failed
        .futureValue

      assert(failure.isInstanceOf[SpotifyClientException.InvalidResponse])
    }

    Scenario("HTTP statusをSpotifyClientExceptionへ分類する") {
      val cases = Seq(
        400 -> ((failure: Throwable) => failure.isInstanceOf[SpotifyClientException.ClientError]),
        401 -> ((failure: Throwable) => failure.isInstanceOf[SpotifyClientException.Unauthorized]),
        403 -> ((failure: Throwable) => failure.isInstanceOf[SpotifyClientException.Forbidden]),
        500 -> ((failure: Throwable) => failure.isInstanceOf[SpotifyClientException.ServerError])
      )

      cases.foreach { case (statusCode, isExpectedFailure) =>
        withServer { exchange =>
          respond(exchange, statusCode, s"""{"error":{"status":$statusCode}}""")
        } { baseUrl =>
          val failure = playlistApi(baseUrl)
            .getCurrentUserPlaylistPage("access-token", limit = 10, offset = 0)
            .failed
            .futureValue

          assert(isExpectedFailure(failure))
        }
      }
    }

    Scenario("JSONをdecodeできない成功レスポンスをInvalidResponseとして扱う") {
      withServer { exchange =>
        respond(exchange, 200, """{"unexpected":true}""")
      } { baseUrl =>
        val failure = playlistApi(baseUrl)
          .getCurrentUserPlaylistPage("access-token", limit = 10, offset = 0)
          .failed
          .futureValue

        assert(failure.isInstanceOf[SpotifyClientException.InvalidResponse])
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 3.seconds, interval = 10.millis)

  private def playlistApi(apiBaseUrl: String): SpotifyPlaylistApi = {
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
    val requestExecutor = new SpotifyRequestExecutor(config, ioDispatcher, throttler)
    new SpotifyPlaylistApi(config, requestExecutor, ioDispatcher)
  }

  private def withServer(
      handler: HttpExchange => Unit
  )(
      test: String => Unit
  ): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit =
          handler(exchange)
      }
    )
    server.start()
    try {
      test(s"http://127.0.0.1:${server.getAddress.getPort}/v1")
    } finally {
      server.stop(0)
    }
  }

  private def readBody(exchange: HttpExchange): String =
    try String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
    finally exchange.getRequestBody.close()

  private def respond(
      exchange: HttpExchange,
      statusCode: Int,
      body: String
  ): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(statusCode, bytes.length)
    val responseBody = exchange.getResponseBody
    try responseBody.write(bytes)
    finally {
      responseBody.close()
      exchange.close()
    }
  }

  private object ioDispatcher extends IoDispatcher {
    override def execute(runnable: Runnable): Unit =
      ExecutionContext.global.execute(runnable)

    override def reportFailure(cause: Throwable): Unit =
      ExecutionContext.global.reportFailure(cause)
  }
}
