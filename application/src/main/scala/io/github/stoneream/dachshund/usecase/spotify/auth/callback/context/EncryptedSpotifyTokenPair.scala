package io.github.stoneream.dachshund.usecase.spotify.auth.callback.context

import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken

private[callback] final case class EncryptedSpotifyTokenPair(
    accessToken: EncryptedSpotifyToken,
    refreshToken: EncryptedSpotifyToken
)
