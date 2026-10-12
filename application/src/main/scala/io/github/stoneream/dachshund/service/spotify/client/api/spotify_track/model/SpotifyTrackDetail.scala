package io.github.stoneream.dachshund.service.spotify.client.api.spotify_track.model

final case class SpotifyTrackDetail(
    spotifyTrackCode: String,
    trackName: String,
    artistNames: Seq[String],
    albumName: String,
    imageUrl: Option[String],
    durationMs: Option[Int],
    popularity: Option[Int]
)
