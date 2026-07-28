package io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.model

final case class SpotifyCreatePlaylistResult(
    spotifyPlaylistCode: String,
    playlistName: String,
    spotifyPlaylistUri: String
)
