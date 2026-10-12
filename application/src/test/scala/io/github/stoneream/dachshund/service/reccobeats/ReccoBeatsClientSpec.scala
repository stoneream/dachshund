package io.github.stoneream.dachshund.service.reccobeats

import com.sun.net.httpserver.HttpServer
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class ReccoBeatsClientSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("reccobeats-client-spec")
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 10.millis)
  private val trackId = "00vJzaoxM3Eja1doBUhX0P"
  private val href = s"https://open.spotify.com/track/$trackId"

  Feature("ReccoBeats の音響指標取得") {
    Scenario("Spotify ID を指定し認証情報を送らず Key・mode・tempo と 7 指標を取得する") {
      val body = s"""{"content":[{"id":"878dadea-33c5-4c08-bdb9-e2b117475a99","href":"$href",
        "key":7,"mode":0,"tempo":100.5,"energy":0.625,"danceability":0.75,"valence":0.5,
        "acousticness":0,"instrumentalness":1,"liveness":0.1,"speechiness":0.2}]}"""
      withServer(200, body) { (client, requests) =>
        val audio = client.getTrackAudioFeatures(trackId).futureValue.get
        assert(audio.key.contains(7))
        assert(audio.mode.contains(0))
        assert(audio.tempo.contains(100.5))
        assert(audio.energy.contains(0.625))
        assert(audio.danceability.contains(0.75))
        assert(audio.valence.contains(0.5))
        assert(audio.acousticness.contains(0))
        assert(audio.instrumentalness.contains(1))
        assert(audio.liveness.contains(0.1))
        assert(audio.speechiness.contains(0.2))
        assert(requests.asScala.toSeq == Seq(CapturedRequest("GET", "/v1/audio-features", Some(s"ids=$trackId"), None, None)))
      }
    }

    Scenario("未登録の空配列と 404 は音響指標なしとして返す") {
      Seq(200 -> """{"content":[]}""", 404 -> "not found").foreach { (status, body) =>
        withServer(status, body) { (client, _) => assert(client.getTrackAudioFeatures(trackId).futureValue.isEmpty) }
      }
    }

    Scenario("欠損・null と推定不能な値を 0 に変換しない") {
      Seq(
        s"""{"href":"$href","key":null,"tempo":null}""",
        s"""{"href":"$href","key":-1,"mode":2,"tempo":0,"energy":1.1,"valence":-0.1}""",
        s"""{"href":"$href","key":12,"mode":-1,"tempo":-1,"acousticness":-1,"instrumentalness":2,"liveness":-1,"speechiness":2}"""
      ).foreach { entry =>
        withServer(200, s"""{"content":[$entry]}""") { (client, _) =>
          assert(client.getTrackAudioFeatures(trackId).futureValue.get.productIterator.forall(_ == None))
        }
      }
    }

    Scenario("不正な JSON・型・応答構造は InvalidResponse に分類する") {
      Seq(
        "not json",
        "null",
        "[]",
        "{}",
        """{"content":null}""",
        """{"content":[null]}""",
        """{"content":[{}]}""",
        s"""{"content":[{"href":"$href","key":1.5}]}""",
        s"""{"content":[{"href":"$href","key":"7"}]}""",
        s"""{"content":[{"href":"$href","tempo":"100"}]}"""
      ).foreach { body =>
        withClue(s"response: $body") {
          withServer(200, body) { (client, _) =>
            assert(client.getTrackAudioFeatures(trackId).failed.futureValue.isInstanceOf[ReccoBeatsClientException.InvalidResponse])
          }
        }
      }
    }

    Scenario("別の曲や複数曲の応答を要求した曲の指標として返さない") {
      Seq(
        """{"content":[{"href":"https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC","tempo":100}]}""",
        s"""{"content":[{"href":"$href"},{"href":"$href"}]}"""
      ).foreach { body =>
        withServer(200, body) { (client, _) =>
          assert(client.getTrackAudioFeatures(trackId).failed.futureValue.isInstanceOf[ReccoBeatsClientException.InvalidResponse])
        }
      }
    }

    Scenario("401・403 は Spotify の再認可ではなく ReccoBeats のアクセス拒否として返す") {
      Seq(401, 403).foreach { status =>
        withServer(status, "provider-secret") { (client, _) =>
          val failure = client.getTrackAudioFeatures(trackId).failed.futureValue
          assert(failure == ReccoBeatsClientException.AccessDenied(status))
          assert(!failure.getMessage.contains("provider-secret"))
        }
      }
    }

    Scenario("429 の待機秒数を返し自動再試行しない") {
      Seq(
        Some("12") -> Some(12.seconds),
        Some("0") -> Some(Duration.Zero),
        None -> None,
        Some("invalid") -> None,
        Some("-1") -> None,
        Some(Long.MaxValue.toString) -> None
      ).foreach { (header, expected) =>
        withServer(429, "provider-secret", header.map("Retry-After" -> _).toMap) { (client, requests) =>
          val failure = client.getTrackAudioFeatures(trackId).failed.futureValue.asInstanceOf[ReccoBeatsClientException.RateLimited]
          assert(failure.retryAfter == expected)
          assert(requests.size() == 1)
          assert(!failure.getMessage.contains("provider-secret"))
        }
      }
    }

    Scenario("HTTP-date の Retry-After を待機時間に変換する") {
      val retryAt = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(1).format(DateTimeFormatter.RFC_1123_DATE_TIME)
      withServer(429, "", Map("Retry-After" -> retryAt)) { (client, _) =>
        val failure = client.getTrackAudioFeatures(trackId).failed.futureValue.asInstanceOf[ReccoBeatsClientException.RateLimited]
        assert(failure.retryAfter.exists(delay => delay > 50.seconds && delay <= 60.seconds))
      }
      withServer(429, "", Map("Retry-After" -> "Sun, 06 Nov 1994 08:49:37 GMT")) { (client, _) =>
        val failure = client.getTrackAudioFeatures(trackId).failed.futureValue.asInstanceOf[ReccoBeatsClientException.RateLimited]
        assert(failure.retryAfter.contains(Duration.Zero))
      }
    }

    Scenario("その他の HTTP エラーは本文を公開せずステータスを保持する") {
      Seq(400, 500, 503).foreach { status =>
        withServer(status, "provider-secret") { (client, _) =>
          assert(client.getTrackAudioFeatures(trackId).failed.futureValue == ReccoBeatsClientException.HttpError(status))
        }
      }
    }

    Scenario("リダイレクト先を呼び出さない") {
      withServer(302, "", Map("Location" -> "/v1/redirected")) { (client, requests) =>
        assert(client.getTrackAudioFeatures(trackId).failed.futureValue == ReccoBeatsClientException.HttpError(302))
        assert(requests.size() == 1)
      }
    }

    Scenario("不正な Spotify ID は HTTP リクエストを送信しない") {
      withServer(200, "") { (client, requests) =>
        Seq("", "short", "a" * 21 + "_", "https://example.com/", "a" * 22 + "&ids=another").foreach { id =>
          assert(client.getTrackAudioFeatures(id).failed.futureValue.isInstanceOf[IllegalArgumentException])
        }
        assert(requests.isEmpty)
      }
    }

    Scenario("応答が遅い場合はタイムアウトし Network として返す") {
      withServer(200, "", responseDelay = 300.millis, requestTimeout = 50.millis) { (client, _) =>
        assert(client.getTrackAudioFeatures(trackId).failed.futureValue.isInstanceOf[ReccoBeatsClientException.Network])
      }
    }
  }

  private case class CapturedRequest(method: String, path: String, query: Option[String], authorization: Option[String], cookie: Option[String])

  private def withServer(
      status: Int,
      body: String,
      headers: Map[String, String] = Map.empty,
      responseDelay: FiniteDuration = Duration.Zero,
      requestTimeout: FiniteDuration = 3.seconds
  )(test: (ReccoBeatsClient, ConcurrentLinkedQueue[CapturedRequest]) => Unit): Unit = {
    val requests = new ConcurrentLinkedQueue[CapturedRequest]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/v1/",
      exchange => {
        requests.add(
          CapturedRequest(
            exchange.getRequestMethod,
            exchange.getRequestURI.getPath,
            Option(exchange.getRequestURI.getRawQuery),
            Option(exchange.getRequestHeaders.getFirst("Authorization")),
            Option(exchange.getRequestHeaders.getFirst("Cookie"))
          )
        )
        try {
          if (responseDelay > Duration.Zero) Thread.sleep(responseDelay.toMillis)
          headers.foreach { (name, value) => exchange.getResponseHeaders.add(name, value) }
          val bytes = body.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(status, bytes.length)
          exchange.getResponseBody.write(bytes)
        } finally exchange.close()
      }
    )
    server.start()
    try {
      val base = TestApplicationConfig()
      val config =
        base.copy(reccobeats = base.reccobeats.copy(apiBaseUrl = s"http://127.0.0.1:${server.getAddress.getPort}/v1", requestTimeout = requestTimeout))
      test(new ReccoBeatsClient(config, ioDispatcher), requests)
    } finally server.stop(0)
  }

  private object ioDispatcher extends IoDispatcher {
    override def execute(runnable: Runnable): Unit = ExecutionContext.global.execute(runnable)
    override def reportFailure(cause: Throwable): Unit = ExecutionContext.global.reportFailure(cause)
  }
}
