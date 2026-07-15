package io.github.stoneream.dachshund.service.spotify.client

import org.scalatest.featurespec.AnyFeatureSpec

class SpotifyReleaseTypeSpec extends AnyFeatureSpec {
  Feature("Spotify release type") {
    Scenario("Spotify album_type を大文字化して release_type にする") {
      assert(SpotifyReleaseType.fromAlbumType("album") == "ALBUM")
      assert(SpotifyReleaseType.fromAlbumType("single") == "SINGLE")
    }

    Scenario("single は EP に変換しない") {
      assert(SpotifyReleaseType.fromAlbumType("single") != "EP")
    }
  }
}
