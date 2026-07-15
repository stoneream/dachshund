package io.github.stoneream.dachshund.service.spotify.auth.access_token.context

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken

private[access_token] final case class SpotifyAccessTokenRefreshedTokens(
    accessToken: String,
    encryptedAccessToken: EncryptedSpotifyToken,
    encryptedRefreshToken: EncryptedSpotifyToken,
    tokenType: String,
    scopeText: String,
    accessTokenExpiresAt: BusinessDateTime,
    nextRefreshAttemptAt: BusinessDateTime
)
