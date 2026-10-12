package io.github.stoneream.dachshund.service.reccobeats.model

final case class ReccoBeatsAudioFeatures(
    key: Option[Int],
    mode: Option[Int],
    tempo: Option[Double],
    energy: Option[Double],
    danceability: Option[Double],
    valence: Option[Double],
    acousticness: Option[Double],
    instrumentalness: Option[Double],
    liveness: Option[Double],
    speechiness: Option[Double]
)
