package io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model

final case class SpotifyTrackSearchItem(
    spotifyTrackCode: String,
    trackName: String,
    artistNames: Seq[String],
    albumName: String,
    imageUrl: Option[String]
)
