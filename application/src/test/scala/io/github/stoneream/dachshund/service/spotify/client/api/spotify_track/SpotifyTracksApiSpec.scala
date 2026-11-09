package io.github.stoneream.dachshund.service.spotify.client.api.spotify_track

import com.sun.net.httpserver.HttpServer
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
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class SpotifyTracksApiSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-tracks-api-spec")
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 10.millis)
  private val trackId = "4uLU6hMCjMI75M1A2tKUQC"

  Feature("Spotify 楽曲情報 API の SDK 境界") {
    Scenario("Get Track はユーザートークンで取得し market を指定せず表示項目を返す") {
      val body = s"""{"id":"$trackId","name":"曲A","artists":[{"name":"歌手A"},{"name":"歌手B"}],
        "album":{"name":"アルバムA","images":[{"url":"https://i.scdn.co/image/cover"}]},"duration_ms":180999,"popularity":42}"""
      withServer(200, body) { (api, requests) =>
        val track = api.getTrack("user-token", trackId).futureValue.get
        assert(track.spotifyTrackCode == trackId)
        assert(track.trackName == "曲A")
        assert(track.artistNames == Seq("歌手A", "歌手B"))
        assert(track.albumName == "アルバムA")
        assert(track.imageUrl.contains("https://i.scdn.co/image/cover"))
        assert(track.durationMs.contains(180999))
        assert(track.popularity.contains(42))
        assert(requests.asScala.toSeq == Seq(CapturedRequest(s"/v1/tracks/$trackId", None, "Bearer user-token")))
      }
    }

    Scenario("省略された popularity・duration を 0 に変換しない") {
      withServer(200, s"""{"id":"$trackId"}""") { (api, _) =>
        val track = api.getTrack("token", trackId).futureValue.get
        assert(track.durationMs.isEmpty)
        assert(track.popularity.isEmpty)
        assert(track.imageUrl.isEmpty)
        assert(track.artistNames.isEmpty)
      }
    }

    Scenario("範囲外の duration・popularity を欠損として扱う") {
      withServer(200, s"""{"id":"$trackId","duration_ms":-1,"popularity":101}""") { (api, _) =>
        val track = api.getTrack("token", trackId).futureValue.get
        assert(track.durationMs.isEmpty)
        assert(track.popularity.isEmpty)
      }
    }

    Scenario("404 だけは詳細 API 内で None に変換する") {
      withServer(404, """{"error":{"status":404,"message":"missing"}}""") { (api, _) =>
        assert(api.getTrack("token", trackId).futureValue.isEmpty)
      }
    }

    Seq(401, 403, 500).foreach { status =>
      Scenario(s"$status は既存の SpotifyClientException に分類する") {
        withServer(status, s"""{"error":{"status":$status,"message":"provider-secret"}}""") { (api, _) =>
          val failure = api.getTrack("token", trackId).failed.futureValue
          locally {
            status match {
              case 401 => assert(failure.isInstanceOf[SpotifyClientException.Unauthorized])
              case 403 => assert(failure.isInstanceOf[SpotifyClientException.Forbidden])
              case _ => assert(failure.isInstanceOf[SpotifyClientException.ServerError])
            }
          }
        }
      }
    }

    Scenario("429 の Retry-After を保持し後続リクエストを停止する") {
      withServer(429, """{"error":{"status":429,"message":"limited"}}""", Map("Retry-After" -> "10")) { (api, requests) =>
        val failure = api.getTrack("token", trackId).failed.futureValue.asInstanceOf[SpotifyClientException.RateLimited]
        assert(failure.retryAfter.contains(10.seconds))
        assert(api.getTrack("token", trackId).failed.futureValue.isInstanceOf[SpotifyClientException.RateLimited])
        assert(requests.size() == 1)
      }
    }

    Scenario("不正な JSON や ID 欠損は InvalidResponse に分類する") {
      Seq("not json", "null", "[]", "{}", """{"id":"invalid"}""", s"""{"id":"$trackId","duration_ms":"invalid"}""").foreach { body =>
        withServer(200, body) { (api, _) =>
          assert(api.getTrack("token", trackId).failed.futureValue.isInstanceOf[SpotifyClientException.InvalidResponse])
        }
      }

    }
  }

  private case class CapturedRequest(path: String, query: Option[String], authorization: String)

  private def withServer(status: Int, body: String, headers: Map[String, String] = Map.empty)(
      test: (SpotifyTracksApi, ConcurrentLinkedQueue[CapturedRequest]) => Unit
  ): Unit = {
    val requests = new ConcurrentLinkedQueue[CapturedRequest]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/v1/",
      exchange => {
        requests.add(
          CapturedRequest(exchange.getRequestURI.getPath, Option(exchange.getRequestURI.getRawQuery), exchange.getRequestHeaders.getFirst("Authorization"))
        )
        headers.foreach { (name, value) => exchange.getResponseHeaders.add(name, value) }
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.length)
        try exchange.getResponseBody.write(bytes)
        finally exchange.close()
      }
    )
    server.start()
    try {
      val base = TestApplicationConfig()
      val config = base.copy(spotify =
        base.spotify.copy(client =
          base.spotify.client.copy(
            apiBaseUrl = s"http://127.0.0.1:${server.getAddress.getPort}/v1",
            requestPolicy = SpotifyRequestPolicyConfig(Duration.Zero, 30.seconds)
          )
        )
      )
      val executor = new SpotifyRequestExecutor(config, ioDispatcher, new SpotifyRequestThrottler(config))
      test(new SpotifyTracksApi(executor, ioDispatcher), requests)
    } finally server.stop(0)
  }

  private object ioDispatcher extends IoDispatcher {
    override def execute(runnable: Runnable): Unit = ExecutionContext.global.execute(runnable)
    override def reportFailure(cause: Throwable): Unit = ExecutionContext.global.reportFailure(cause)
  }
}
