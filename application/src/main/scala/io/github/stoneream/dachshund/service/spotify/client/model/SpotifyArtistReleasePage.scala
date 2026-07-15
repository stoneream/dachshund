package io.github.stoneream.dachshund.service.spotify.client.model

final case class SpotifyArtistReleasePage(
    releases: Seq[SpotifyArtistRelease],
    nextOffset: Option[Int]
)
