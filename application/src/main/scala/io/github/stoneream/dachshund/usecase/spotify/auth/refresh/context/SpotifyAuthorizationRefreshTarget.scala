package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken
import io.github.stoneream.dachshund.model.QueueJobStatus

private[refresh] final case class SpotifyAuthorizationRefreshTarget(
    authorizationId: Long,
    queueId: Long,
    userId: Long,
    scopeText: String,
    encryptedRefreshToken: EncryptedSpotifyToken,
    tokenType: String,
    accessTokenExpiresAt: BusinessDateTime,
    refreshMarginSeconds: Int,
    lastAuthorizedAt: Option[BusinessDateTime],
    lastRefreshedAt: Option[BusinessDateTime],
    queueStatus: QueueJobStatus,
    attemptCount: Int,
    nextAttemptAt: Option[BusinessDateTime],
    lastAttemptedAt: Option[BusinessDateTime],
    completedAt: Option[BusinessDateTime],
    lastFailedAt: Option[BusinessDateTime],
    lastErrorType: String,
    lockToken: String,
    lockedUntil: Option[BusinessDateTime],
    authorizationLockVersion: Long,
    queueLockVersion: Long,
    queueDeleted: Long
)
