package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserSpotifyAuthorizationRefreshQueueDbRow(
    id: Long,
    authorizationId: Long,
    status: String,
    nextAttemptAt: Option[LocalDateTime],
    attemptCount: Int,
    lastFailedAt: Option[LocalDateTime],
    lastErrorType: String,
    lockToken: String,
    lockedUntil: Option[LocalDateTime],
    lastAttemptedAt: Option[LocalDateTime],
    completedAt: Option[LocalDateTime],
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
