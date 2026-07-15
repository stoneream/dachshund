package io.github.stoneream.dachshund.service.spotify.auth.access_token.model

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken

final case class SpotifyAccessTokenResolveTarget(
    authorizationId: Long,
    queueId: Long,
    userId: Long,
    scopeText: String,
    encryptedAccessToken: EncryptedSpotifyToken,
    encryptedRefreshToken: EncryptedSpotifyToken,
    tokenType: String,
    accessTokenExpiresAt: BusinessDateTime,
    refreshMarginSeconds: Int,
    attemptCount: Int,
    nextAttemptAt: Option[BusinessDateTime],
    lastErrorType: Option[String],
    queueStatus: String,
    authorizationLockVersion: Long,
    queueLockVersion: Long
)
