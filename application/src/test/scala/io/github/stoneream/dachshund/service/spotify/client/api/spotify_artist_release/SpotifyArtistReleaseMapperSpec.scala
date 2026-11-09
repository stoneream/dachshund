package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.SpotifyArtistReleaseSummary
import io.circe.Decoder
import io.circe.parser.decode
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.SpotifyArtistReleaseResponse.{AlbumResponse, TrackResponse}
import org.scalatest.featurespec.AnyFeatureSpec

class SpotifyArtistReleaseMapperSpec extends AnyFeatureSpec {
  Feature("Spotify artist release mapping") {
    Scenario("summaryのSpotify IDがない要素を除外する") {
      val summary = json[AlbumResponse]("""{"name":"No ID"}""")

      assert(SpotifyArtistReleaseMapper.toSummary(summary).isEmpty)
    }

    Scenario("album summaryをアプリケーションモデルへ変換する") {
      val summary = json[AlbumResponse]("""{
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
      val detail = json[AlbumResponse]("""{
        "id":"detail-release", "album_type":"album", "label":"  Label   Name  ",
        "popularity":80, "available_markets":["JP"]
      }""")
      val track = json[TrackResponse]("""{
        "id":"track-1", "name":"Track 1", "uri":"spotify:track:track-1",
        "disc_number":1, "track_number":2, "duration_ms":1234, "explicit":true, "is_playable":false,
        "available_markets":["JP","US"], "linked_from":{"id":"original-1","uri":"spotify:track:original-1"}
      }""")

      val result = SpotifyArtistReleaseMapper.toRelease(
        sourceSpotifyArtistCode = "artist-1",
        summary = summary,
        detail = detail,
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

    Scenario("既知のenum値は小文字に正規化し、未知の値はfallbackまたはnullにする") {
      val summary = SpotifyArtistReleaseMapper
        .toSummary(json[AlbumResponse]("""{
        "id":"release-1", "album_type":"ALBUM", "release_date_precision":"DAY"
      }"""))
        .get
      assert(summary.albumType == "album")
      assert(summary.releaseDatePrecision == "day")

      val known = SpotifyArtistReleaseMapper.toRelease(
        "artist-1",
        summary,
        json[AlbumResponse]("""{
        "album_type":"SINGLE", "release_date_precision":"MONTH", "copyrights":[{"type":"C"},{"type":"P"}]
      }"""),
        Seq.empty
      )
      assert(known.albumType == "single")
      assert(known.releaseDatePrecision == "month")
      assert(known.copyrightsJson.contains("""[{"text":null,"type":"c"},{"text":null,"type":"p"}]"""))

      val unknownDetail = json[AlbumResponse]("""{
        "id":"release-1", "album_type":"UNKNOWN", "release_date_precision":"UNKNOWN", "copyrights":[{"type":"UNKNOWN"}]
      }""")
      val unknownSummary = SpotifyArtistReleaseMapper.toSummary(unknownDetail).get
      assert(unknownSummary.albumType.isEmpty)
      assert(unknownSummary.releaseDatePrecision.isEmpty)
      val fallback = SpotifyArtistReleaseMapper.toRelease("artist-1", summary, unknownDetail, Seq.empty)
      assert(fallback.albumType == "album")
      assert(fallback.releaseDatePrecision == "day")
      assert(fallback.copyrightsJson.contains("""[{"text":null,"type":null}]"""))
    }

    Scenario("detailの画像が省略またはnullならsummaryへfallbackし、空配列なら画像なしにする") {
      val summary = SpotifyArtistReleaseMapper
        .toSummary(json[AlbumResponse]("""{
        "id":"release-1", "images":[{"url":"https://image.test/summary", "height":640, "width":640}]
      }"""))
        .get

      Seq("""{"id":"release-1"}""", """{"id":"release-1","images":null}""").foreach { value =>
        val result = SpotifyArtistReleaseMapper.toRelease("artist-1", summary, json[AlbumResponse](value), Seq.empty)
        assert(result.primaryImageUrl == "https://image.test/summary")
        assert(result.primaryImageHeight.contains(640))
        assert(result.imagesJson.contains("""[{"url":"https://image.test/summary","height":640,"width":640}]"""))
      }

      val empty = SpotifyArtistReleaseMapper.toRelease("artist-1", summary, json[AlbumResponse]("""{"id":"release-1","images":[]}"""), Seq.empty)
      assert(empty.primaryImageUrl.isEmpty)
      assert(empty.primaryImageHeight.isEmpty)
      assert(empty.imagesJson.isEmpty)
    }

    Scenario("IDのないtrackを除外してもtotalTracksCountには取得した全要素を数える") {
      val detail = json[AlbumResponse]("""{"id":"release-1"}""")
      val summary = SpotifyArtistReleaseMapper.toSummary(detail).get
      val tracks = json[Seq[TrackResponse]]("""[{"id":" track-1 "},{},{"id":null},{"id":"  "}]""")

      val result = SpotifyArtistReleaseMapper.toRelease("artist-1", summary, detail, tracks)

      assert(result.totalTracksCount.contains(4))
      assert(result.tracks.map(_.spotifyTrackCode) == Seq("track-1"))
    }

    Scenario("省略またはnullのメタデータを欠損値のまま返す") {
      Seq(
        """{"id":"release-1"}""",
        """{
        "id":"release-1", "album_group":null, "restrictions":null, "available_markets":null, "popularity":null
      }"""
      ).foreach { value =>
        val detail = json[AlbumResponse](value)
        val summary = SpotifyArtistReleaseMapper.toSummary(detail).get
        val tracks = json[Seq[TrackResponse]]("""[
          {"id":"track-1"},
          {"id":"track-2", "available_markets":null, "linked_from":null, "explicit":null, "is_playable":null}
        ]""")

        val result = SpotifyArtistReleaseMapper.toRelease("artist-1", summary, detail, tracks)

        assert(result.albumGroup.isEmpty)
        assert(result.availableMarketsJson.isEmpty)
        assert(result.popularity.isEmpty)
        assert(result.restrictionsJson.isEmpty)
        result.tracks.foreach { track =>
          assert(track.availableMarketsJson.isEmpty)
          assert(track.linkedFromSpotifyTrackCode.isEmpty)
          assert(track.linkedFromSpotifyTrackUri.isEmpty)
          assert(track.explicit.isEmpty)
          assert(track.isPlayable.isEmpty)
        }
      }
    }
  }

  private def json[A: Decoder](value: String): A = decode[A](value).fold(throw _, identity)
}
