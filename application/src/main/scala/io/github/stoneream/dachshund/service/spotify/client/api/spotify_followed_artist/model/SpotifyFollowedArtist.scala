package io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model

final case class SpotifyFollowedArtist(
    spotifyArtistCode: String,
    artistName: String,
    spotifyArtistUri: String,
    spotifyUrl: String,
    href: String,
    primaryImageUrl: String,
    primaryImageHeight: Option[Int],
    primaryImageWidth: Option[Int],
    imagesJson: Option[String],
    genresJson: Option[String],
    followersTotal: Option[Long],
    popularity: Option[Int]
)
