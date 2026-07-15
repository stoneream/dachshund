package io.github.stoneream.dachshund.service.spotify.client.model

final case class SpotifyFollowedArtistsPage(
    artists: Seq[SpotifyFollowedArtist],
    nextAfterCursor: Option[String]
) {
  val isLastPage: Boolean = nextAfterCursor.isEmpty
}
