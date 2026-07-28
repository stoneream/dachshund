package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

private[spotify_artist_release] object SpotifyReleaseType {
  def fromAlbumType(albumType: String): String =
    albumType.toUpperCase(java.util.Locale.ROOT)
}
