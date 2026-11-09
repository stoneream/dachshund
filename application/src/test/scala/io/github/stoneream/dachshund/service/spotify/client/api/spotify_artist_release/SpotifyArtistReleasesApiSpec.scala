package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import com.sun.net.httpserver.HttpServer
import io.github.stoneream.dachshund.config.spotify.SpotifyRequestPolicyConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
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

class SpotifyArtistReleasesApiSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-artist-releases-api-spec")
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 10.millis)

  Feature("Spotify リリース取得の条件と応答") {
    Scenario("一覧の取得条件と全トラックのページングを保ち SDK モデルにない値も返す") {
      withServer { (api, requests) =>
        val page = api.getArtistReleaseSummaryPage("token", "artist-1", "album,single", Some("jp"), 20, 40).futureValue
        assert(page.nextOffset.contains(60))
        assert(page.releases.head.albumGroup.contains("appears_on"))
        val release = api.getArtistRelease("token", "artist-1", page.releases.head, Some("jp")).futureValue
        assert(release.albumGroup.contains("appears_on"))
        assert(release.availableMarketsJson.contains("""["JP"]"""))
        assert(release.popularity.contains(80))
        assert(release.totalTracksCount.contains(2))
        assert(release.tracks.map(_.spotifyTrackCode) == Seq("track-1", "track-2"))
        assert(release.tracks.map(_.availableMarketsJson) == Seq(Some("""["JP"]"""), Some("""["US"]""")))
        assert(release.tracks.last.linkedFromSpotifyTrackCode.contains("original-2"))
        assert(release.tracks.last.linkedFromSpotifyTrackUri.contains("spotify:track:original-2"))
        assert(
          requests.asScala.toSeq == Seq(
            "/v1/artists/artist-1/albums" -> Set("include_groups=album%2Csingle", "market=JP", "limit=20", "offset=40"),
            "/v1/albums/release-1" -> Set("market=JP"),
            "/v1/albums/release-1/tracks" -> Set("market=JP", "limit=50", "offset=1")
          )
        )
      }
    }

    Scenario("API が項目を省略した場合だけ欠損値とする") {
      withServer { (api, _) =>
        val page = api.getArtistReleaseSummaryPage("token", "artist-1", "album", None, 20, 0).futureValue
        val release = api.getArtistRelease("token", "artist-1", page.releases.head, None).futureValue
        assert(release.availableMarketsJson.isEmpty)
        assert(release.popularity.isEmpty)
        assert(release.tracks.head.availableMarketsJson.isEmpty)
        assert(release.tracks.head.linkedFromSpotifyTrackCode.isEmpty)
      }
    }
  }

  private def withServer(test: (SpotifyArtistReleasesApi, ConcurrentLinkedQueue[(String, Set[String])]) => Unit): Unit = {
    val requests = new ConcurrentLinkedQueue[(String, Set[String])]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/v1/",
      exchange => {
        val path = exchange.getRequestURI.getPath
        val query = Option(exchange.getRequestURI.getRawQuery).map(_.split("&").toSet).getOrElse(Set.empty[String])
        requests.add(path -> query)
        val hasMarket = query.contains("market=JP")
        val body = path match {
          case "/v1/artists/artist-1/albums" =>
            """{"items":[{"id":"release-1","album_type":"album","album_group":"appears_on"}],"next":"next","offset":40,"limit":20}"""
          case "/v1/albums/release-1" if hasMarket =>
            """{"id":"release-1","available_markets":["JP"],"popularity":80,"tracks":{"items":[{"id":"track-1","available_markets":["JP"]}],"next":"next","offset":0,"limit":1}}"""
          case "/v1/albums/release-1/tracks" =>
            """{"items":[{"id":"track-2","available_markets":["US"],"linked_from":{"id":"original-2","uri":"spotify:track:original-2"}}],"next":null}"""
          case _ => """{"id":"release-1","tracks":{"items":[{"id":"track-1"}],"next":null}}"""
        }
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.length)
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
      test(new SpotifyArtistReleasesApi(executor, ioDispatcher), requests)
    } finally server.stop(0)
  }

  private object ioDispatcher extends IoDispatcher {
    override def execute(runnable: Runnable): Unit = ExecutionContext.global.execute(runnable)
    override def reportFailure(cause: Throwable): Unit = ExecutionContext.global.reportFailure(cause)
  }
}
