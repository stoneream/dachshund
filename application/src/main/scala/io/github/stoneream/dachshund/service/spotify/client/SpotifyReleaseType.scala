package io.github.stoneream.dachshund.service.spotify.client

private[client] object SpotifyReleaseType {
  def fromAlbumType(albumType: String): String =
    albumType.toUpperCase(java.util.Locale.ROOT)
}
