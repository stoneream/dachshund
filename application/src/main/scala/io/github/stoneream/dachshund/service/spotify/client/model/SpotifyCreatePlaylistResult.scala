package io.github.stoneream.dachshund.service.spotify.client.model

final case class SpotifyCreatePlaylistResult(
    spotifyPlaylistCode: String,
    playlistName: String,
    spotifyPlaylistUri: String
)
