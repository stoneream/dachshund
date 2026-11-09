package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.SpotifyArtistReleaseSummary
import com.google.gson.{JsonObject, JsonParser}
import org.scalatest.featurespec.AnyFeatureSpec

class SpotifyArtistReleaseMapperSpec extends AnyFeatureSpec {
  Feature("Spotify artist release mapping") {
    Scenario("summaryのSpotify IDがない要素を除外する") {
      val summary = json("""{"name":"No ID"}""")

      assert(SpotifyArtistReleaseMapper.toSummary(summary).isEmpty)
    }

    Scenario("album summaryをアプリケーションモデルへ変換する") {
      val summary = json("""{
        "id":"release-1", "name":"Release 1", "album_type":"single", "album_group":"single",
        "uri":"spotify:album:release-1", "href":"https://api.spotify.test/albums/release-1",
        "images":[{"url":"https://image.test/1", "height":640, "width":640}],
        "release_date":"2026-07", "release_date_precision":"month"
      }""")

      val result = SpotifyArtistReleaseMapper.toSummary(summary).get

      assert(result.spotifyReleaseCode == "release-1")
      assert(result.albumType == "single")
      assert(result.albumGroup.contains("single"))
      assert(result.images.map(_.url) == Seq("https://image.test/1"))
      assert(result.releaseDateText == "2026-07")
      assert(result.releaseDatePrecision == "month")
    }

    Scenario("detailを優先し、summary fallbackとtrack変換を組み合わせる") {
      val summary = SpotifyArtistReleaseSummary(
        spotifyReleaseCode = "summary-release",
        releaseName = "Summary Name",
        albumType = "single",
        albumGroup = Some("single"),
        spotifyReleaseUri = "spotify:album:summary-release",
        spotifyUrl = "https://open.spotify.test/summary-release",
        href = "https://api.spotify.test/albums/summary-release",
        images = Seq.empty,
        releaseDateText = "2026",
        releaseDatePrecision = "year",
        restrictionsJson = None
      )
      val detail = json("""{
        "id":"detail-release", "album_type":"album", "label":"  Label   Name  ",
        "popularity":80, "available_markets":["JP"]
      }""")
      val track = json("""{
        "id":"track-1", "name":"Track 1", "uri":"spotify:track:track-1",
        "disc_number":1, "track_number":2, "duration_ms":1234, "explicit":true, "is_playable":false,
        "available_markets":["JP","US"], "linked_from":{"id":"original-1","uri":"spotify:track:original-1"}
      }""")

      val result = SpotifyArtistReleaseMapper.toRelease(
        sourceSpotifyArtistCode = "artist-1",
        summary = summary,
        detailJson = detail,
        tracks = Seq(track)
      )

      assert(result.spotifyReleaseCode == "detail-release")
      assert(result.releaseName == "Summary Name")
      assert(result.albumType == "album")
      assert(result.labelName.contains("Label   Name"))
      assert(result.normalizedLabelName.contains("label name"))
      assert(result.availableMarketsJson.contains("""["JP"]"""))
      assert(result.popularity.contains(80))
      assert(result.tracks.map(_.spotifyTrackCode) == Seq("track-1"))
      assert(result.tracks.head.explicit.contains(1L))
      assert(result.tracks.head.isPlayable.contains(0L))
      assert(result.tracks.head.availableMarketsJson.contains("""["JP","US"]"""))
      assert(result.tracks.head.linkedFromSpotifyTrackCode.contains("original-1"))
      assert(result.tracks.head.linkedFromSpotifyTrackUri.contains("spotify:track:original-1"))
    }
  }
  private def json(value: String): JsonObject = JsonParser.parseString(value).getAsJsonObject
}
