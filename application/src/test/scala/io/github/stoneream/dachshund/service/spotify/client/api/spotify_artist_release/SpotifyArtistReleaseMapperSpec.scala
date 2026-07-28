package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import com.neovisionaries.i18n.CountryCode
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.SpotifyArtistReleaseSummary
import org.scalatest.featurespec.AnyFeatureSpec
import se.michaelthelin.spotify.enums.{AlbumGroup, AlbumType, ReleaseDatePrecision}
import se.michaelthelin.spotify.model_objects.specification.{Album, AlbumSimplified, Image, TrackSimplified}

class SpotifyArtistReleaseMapperSpec extends AnyFeatureSpec {
  Feature("Spotify artist release mapping") {
    Scenario("summaryのSpotify IDがない要素を除外する") {
      val summary = new AlbumSimplified.Builder()
        .setName("No ID")
        .build()

      assert(SpotifyArtistReleaseMapper.toSummary(summary).isEmpty)
    }

    Scenario("album summaryをアプリケーションモデルへ変換する") {
      val summary = new AlbumSimplified.Builder()
        .setId("release-1")
        .setName("Release 1")
        .setAlbumType(AlbumType.SINGLE)
        .setAlbumGroup(AlbumGroup.SINGLE)
        .setUri("spotify:album:release-1")
        .setHref("https://api.spotify.test/albums/release-1")
        .setImages(new Image.Builder().setUrl("https://image.test/1").setHeight(640).setWidth(640).build())
        .setReleaseDate("2026-07")
        .setReleaseDatePrecision(ReleaseDatePrecision.MONTH)
        .build()

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
      val detail = new Album.Builder()
        .setId("detail-release")
        .setAlbumType(AlbumType.ALBUM)
        .setLabel("  Label   Name  ")
        .setPopularity(80)
        .setAvailableMarkets(CountryCode.JP)
        .build()
      val track = new TrackSimplified.Builder()
        .setId("track-1")
        .setName("Track 1")
        .setUri("spotify:track:track-1")
        .setDiscNumber(1)
        .setTrackNumber(2)
        .setDurationMs(1234)
        .setExplicit(true)
        .setIsPlayable(false)
        .build()

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
    }
  }
}
