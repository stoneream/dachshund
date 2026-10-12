package io.github.stoneream.dachshund.handler.spotify.track.detail

import io.github.stoneream.dachshund.service.spotify.client.api.spotify_track.model.SpotifyTrackDetail
import io.github.stoneream.dachshund.service.reccobeats.model.ReccoBeatsAudioFeatures

private[detail] object TrackDetailPresentation {
  private val Unknown = "—"
  private val KeyNames = Vector("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
  private val MinorCamelot = Vector("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")
  private val MajorCamelot = Vector("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")

  def metrics(track: SpotifyTrackDetail, features: Option[ReccoBeatsAudioFeatures]): Seq[(String, String)] = {
    val tonality = for {
      audio <- features
      key <- audio.key.filter(value => value >= 0 && value <= 11)
      mode <- audio.mode.filter(value => value == 0 || value == 1)
    } yield (key, mode)
    def percent(value: ReccoBeatsAudioFeatures => Option[Double]): String =
      features.flatMap(value).map(score => s"${math.round(score * 100)}%").getOrElse(Unknown)

    Seq(
      "Key" -> tonality.map { case (key, mode) => s"${KeyNames(key)} ${if (mode == 1) "Major" else "Minor"}" }.getOrElse(Unknown),
      "Camelot" -> tonality.map { case (key, mode) => (if (mode == 1) MajorCamelot else MinorCamelot) (key) }.getOrElse(Unknown),
      "BPM" -> features.flatMap(_.tempo).map(value => math.round(value).toString).getOrElse(Unknown),
      "Duration" -> track.durationMs
        .map { millis =>
          val seconds = millis / 1000
          f"${seconds / 60}%d:${seconds % 60}%02d"
        }
        .getOrElse(Unknown),
      "Popularity" -> track.popularity.map(_.toString).getOrElse(Unknown),
      "Energy" -> percent(_.energy),
      "Danceability" -> percent(_.danceability),
      "Happiness" -> percent(_.valence),
      "Acousticness" -> percent(_.acousticness),
      "Instrumentalness" -> percent(_.instrumentalness),
      "Liveness" -> percent(_.liveness),
      "Speechiness" -> percent(_.speechiness)
    )
  }
}
