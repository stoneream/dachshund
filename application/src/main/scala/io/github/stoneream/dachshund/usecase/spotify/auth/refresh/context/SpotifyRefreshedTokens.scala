package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken

private[refresh] final case class SpotifyRefreshedTokens(
    encryptedAccessToken: EncryptedSpotifyToken,
    encryptedRefreshToken: EncryptedSpotifyToken,
    tokenType: String,
    scopeText: String,
    accessTokenExpiresAt: BusinessDateTime,
    nextRefreshAttemptAt: BusinessDateTime
)
