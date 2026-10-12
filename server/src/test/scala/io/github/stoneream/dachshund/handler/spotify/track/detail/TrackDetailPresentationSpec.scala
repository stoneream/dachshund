package io.github.stoneream.dachshund.handler.spotify.track.detail

import io.github.stoneream.dachshund.service.spotify.client.api.spotify_track.model.SpotifyTrackDetail
import io.github.stoneream.dachshund.service.reccobeats.model.ReccoBeatsAudioFeatures
import org.scalatest.featurespec.AnyFeatureSpec

class TrackDetailPresentationSpec extends AnyFeatureSpec {
  private val track = SpotifyTrackDetail("4uLU6hMCjMI75M1A2tKUQC", "track", Seq.empty, "album", None, Some(180999), None)
  private val audio = ReccoBeatsAudioFeatures(None, None, None, None, None, None, None, None, None, None)

  Feature("楽曲指標の表示変換") {
    Scenario("Camelot wheel の 24 調性を major・minor それぞれに対応づける") {
      val wheel = Seq(
        (1, "G♯ Minor", 8, "B Major", 11),
        (2, "D♯ Minor", 3, "F♯ Major", 6),
        (3, "A♯ Minor", 10, "C♯ Major", 1),
        (4, "F Minor", 5, "G♯ Major", 8),
        (5, "C Minor", 0, "D♯ Major", 3),
        (6, "G Minor", 7, "A♯ Major", 10),
        (7, "D Minor", 2, "F Major", 5),
        (8, "A Minor", 9, "C Major", 0),
        (9, "E Minor", 4, "G Major", 7),
        (10, "B Minor", 11, "D Major", 2),
        (11, "F♯ Minor", 6, "A Major", 9),
        (12, "C♯ Minor", 1, "E Major", 4)
      )
      wheel.foreach { (number, minorName, minorKey, majorName, majorKey) =>
        Seq((0, minorKey, minorName, s"${number}A"), (1, majorKey, majorName, s"${number}B")).foreach { (mode, key, name, camelot) =>
          val values = TrackDetailPresentation.metrics(track, Some(audio.copy(key = Some(key), mode = Some(mode)))).toMap
          assert(values("Key") == name)
          assert(values("Camelot") == camelot)
        }
      }
    }

    Scenario("key または mode が不明なら Key と Camelot をともに欠損表示する") {
      Seq((Some(-1), Some(0)), (Some(12), Some(1)), (None, Some(0)), (Some(7), None), (Some(7), Some(2))).foreach { (key, mode) =>
        val values = TrackDetailPresentation.metrics(track, Some(audio.copy(key = key, mode = mode))).toMap
        assert(values("Key") == "—")
        assert(values("Camelot") == "—")
      }
    }

    Scenario("秒の切り捨て、BPM・割合の四捨五入、0 と欠損を区別する") {
      Seq(0 -> "0:00", 59999 -> "0:59", 60000 -> "1:00", 180999 -> "3:00", 3600000 -> "60:00").foreach { (millis, expected) =>
        assert(TrackDetailPresentation.metrics(track.copy(durationMs = Some(millis)), None).toMap.apply("Duration") == expected)
      }
      val values = TrackDetailPresentation
        .metrics(track.copy(popularity = Some(0)), Some(audio.copy(tempo = Some(99.5), energy = Some(0.625), valence = Some(0), danceability = Some(1))))
        .toMap
      assert(values("BPM") == "100")
      assert(values("Energy") == "63%")
      assert(values("Happiness") == "0%")
      assert(values("Danceability") == "100%")
      assert(values("Popularity") == "0")
      assert(values("Speechiness") == "—")
      assert(TrackDetailPresentation.metrics(track.copy(durationMs = None), None).toMap.values.forall(_ == "—"))
    }
  }
}
