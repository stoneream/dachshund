package io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.model

final case class SpotifyPlaylistPage(
    playlists: Seq[SpotifyPlaylist],
    nextOffset: Option[Int]
)
