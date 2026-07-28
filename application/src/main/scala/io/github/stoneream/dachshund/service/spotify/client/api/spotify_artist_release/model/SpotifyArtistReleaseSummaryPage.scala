package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model

final case class SpotifyArtistReleaseSummaryPage(
    releases: Seq[SpotifyArtistReleaseSummary],
    nextOffset: Option[Int]
)
