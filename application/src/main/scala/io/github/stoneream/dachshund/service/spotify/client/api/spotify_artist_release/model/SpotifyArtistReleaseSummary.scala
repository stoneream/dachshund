package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model

final case class SpotifyArtistReleaseSummary(
    spotifyReleaseCode: String,
    releaseName: String,
    albumType: String,
    albumGroup: Option[String],
    spotifyReleaseUri: String,
    spotifyUrl: String,
    href: String,
    images: Seq[SpotifyImage],
    releaseDateText: String,
    releaseDatePrecision: String,
    restrictionsJson: Option[String]
)
