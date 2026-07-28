package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import org.scalatest.featurespec.AnyFeatureSpec

import java.time.LocalDateTime

class SpotifyReleaseDateSpec extends AnyFeatureSpec {
  Feature("Spotify release date") {
    Scenario("day precision の日付を LocalDateTime に変換する") {
      assert(
        SpotifyReleaseDate.releaseDateAt("2026-07-06", "day") == Some(LocalDateTime.of(2026, 7, 6, 0, 0))
      )
    }

    Scenario("年月日で取得できない場合は fallback 日付を返す") {
      assert(
        SpotifyReleaseDate.releaseDateAt("2026-07", "month") == Some(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
      )
      assert(
        SpotifyReleaseDate.releaseDateAt("2026", "year") == Some(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
      )
      assert(
        SpotifyReleaseDate.releaseDateAt("", "day") == Some(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
      )
    }

    Scenario("parse できない day precision は fallback 日付を返す") {
      assert(
        SpotifyReleaseDate.releaseDateAt("2026-02-31", "day") == Some(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
      )
      assert(
        SpotifyReleaseDate.releaseDateAt("unknown", "day") == Some(LocalDateTime.of(9999, 12, 31, 23, 59, 59))
      )
    }
  }
}
