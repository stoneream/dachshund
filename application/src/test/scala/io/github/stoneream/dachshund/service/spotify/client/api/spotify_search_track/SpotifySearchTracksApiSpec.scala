package io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track

import com.sun.net.httpserver.HttpServer
import io.github.stoneream.dachshund.config.spotify.SpotifyRequestPolicyConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model.SpotifyTrackSearchItem
import io.github.stoneream.dachshund.service.spotify.client.lib.{SpotifyRequestExecutor, SpotifyRequestThrottler}
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec

import java.net.{InetSocketAddress, URLDecoder}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class SpotifySearchTracksApiSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-search-tracks-api-spec")
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 10.millis)

  Feature("Spotify の楽曲検索 API") {
    Scenario("検索語とページ情報を送りユーザー用トークンで取得した表示項目を順序通りに返す") {
      val body = """{"tracks":{"offset":10,"limit":10,"total":25,"next":"https://api.spotify.com/v1/search?offset=20","items":[
        {"id":"one","name":"曲A","artists":[{"name":"歌手A"},{"name":"歌手B"}],"album":{"name":"アルバムA","images":[{"url":"https://i.scdn.co/image/cover"}]}},
        {"id":"two","name":"曲B","artists":[{"name":"歌手C"}],"album":{"name":"アルバムB","images":[]}}
      ]}}"""
      withServer(200, body) { (api, requests) =>
        val query = "夜 & artist:星 + #"
        val result = api.searchTracks("user-token", query, 10, 10).futureValue
        val request = requests.asScala.toSeq.head
        assert(request.authorization == "Bearer user-token")
        assert(request.query == Map("q" -> query, "type" -> "track", "limit" -> "10", "offset" -> "10"))
        assert(
          result.tracks == Seq(
            SpotifyTrackSearchItem("one", "曲A", Seq("歌手A", "歌手B"), "アルバムA", Some("https://i.scdn.co/image/cover")),
            SpotifyTrackSearchItem("two", "曲B", Seq("歌手C"), "アルバムB", None)
          )
        )
        assert(result.total == 25)
        assert(result.previousOffset.contains(0))
        assert(result.nextOffset.contains(20))
      }
    }

    Scenario("空の検索結果では前後のページがない") {
      withServer(200, """{"tracks":{"offset":0,"limit":10,"total":0,"next":null,"items":[]}}""") { (api, _) =>
        val result = api.searchTracks("token", "no-results", 10, 0).futureValue
        assert(result.tracks.isEmpty)
        assert(result.previousOffset.isEmpty)
        assert(result.nextOffset.isEmpty)
      }
    }

    Scenario("画像や表示項目が欠けていても楽曲を表示できる") {
      withServer(200, """{"tracks":{"offset":0,"limit":10,"total":1,"next":null,"items":[{"id":"track"}]}}""") { (api, _) =>
        val result = api.searchTracks("token", "query", 10, 0).futureValue
        assert(result.tracks == Seq(SpotifyTrackSearchItem("track", "", Seq.empty, "", None)))
        assert(result.nextOffset.isEmpty)
      }
    }

    Scenario("API が次ページを返しても offset 1000 を超えるリンクを作らない") {
      withServer(200, """{"tracks":{"offset":1000,"limit":10,"total":3000,"next":"https://api.spotify.com/v1/search?offset=1010","items":[]}}""") {
        (api, requests) =>
          val result = api.searchTracks("token", "query", 50, 1000).futureValue
          assert(requests.asScala.toSeq.head.query("limit") == "10")
          assert(result.previousOffset.contains(990))
          assert(result.nextOffset.isEmpty)
      }
    }

    Scenario("401 と 403 は共通クライアント例外として返す") {
      Seq(401, 403).foreach { status =>
        withServer(status, s"""{"error":{"status":$status,"message":"provider message"}}""") { (api, requests) =>
          val failure = api.searchTracks("token", "query", 10, 0).failed.futureValue
          if (status == 401) assert(failure.isInstanceOf[SpotifyClientException.Unauthorized])
          else assert(failure.isInstanceOf[SpotifyClientException.Forbidden])
          assert(requests.size() == 1)
        }
      }
    }

    Scenario("429 の Retry-After を返し停止期間中の次の検索を送信しない") {
      withServer(429, """{"error":{"status":429,"message":"too many requests"}}""", Map("Retry-After" -> "30")) { (api, requests) =>
        val firstFailure = api.searchTracks("token", "query", 10, 0).failed.futureValue
        val secondFailure = api.searchTracks("token", "query", 10, 10).failed.futureValue
        assert(firstFailure.asInstanceOf[SpotifyClientException.RateLimited].retryAfter.contains(30.seconds))
        assert(secondFailure.isInstanceOf[SpotifyClientException.RateLimited])
        assert(requests.size() == 1)
      }
    }
  }

  private case class CapturedRequest(query: Map[String, String], authorization: String)

  private def withServer(status: Int, body: String, headers: Map[String, String] = Map.empty)(
      test: (SpotifySearchTracksApi, ConcurrentLinkedQueue[CapturedRequest]) => Unit
  ): Unit = {
    val requests = new ConcurrentLinkedQueue[CapturedRequest]
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/v1/search",
      exchange => {
        val query = exchange.getRequestURI.getRawQuery
          .split("&")
          .map { parameter =>
            val pair = parameter.split("=", 2)
            URLDecoder.decode(pair(0), StandardCharsets.UTF_8) -> URLDecoder.decode(pair(1), StandardCharsets.UTF_8)
          }
          .toMap
        requests.add(CapturedRequest(query, exchange.getRequestHeaders.getFirst("Authorization")))
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
      test(new SpotifySearchTracksApi(executor, ioDispatcher), requests)
    } finally server.stop(0)
  }

  private object ioDispatcher extends IoDispatcher {
    override def execute(runnable: Runnable): Unit = ExecutionContext.global.execute(runnable)
    override def reportFailure(cause: Throwable): Unit = ExecutionContext.global.reportFailure(cause)
  }
}
