package io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model

final case class SpotifyFollowedArtistsPage(
    artists: Seq[SpotifyFollowedArtist],
    nextAfterCursor: Option[String]
) {
  val isLastPage: Boolean = nextAfterCursor.isEmpty
}
