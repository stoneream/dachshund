package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model

final case class SpotifyImage(
    url: String,
    height: Option[Int],
    width: Option[Int]
)
