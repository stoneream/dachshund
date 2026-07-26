package io.github.stoneream.dachshund.service.spotify.client.model

final case class SpotifyPlaylistPage(
    playlists: Seq[SpotifyPlaylist],
    nextOffset: Option[Int]
)
