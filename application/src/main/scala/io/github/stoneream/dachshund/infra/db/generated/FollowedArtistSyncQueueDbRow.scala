package io.github.stoneream.dachshund.infra.db.generated

import java.time.{LocalDate, LocalDateTime}

final case class FollowedArtistSyncQueueDbRow(
    id: Long,
    userId: Long,
    syncDate: LocalDate,
    status: String,
    requestedLimit: Int,
    afterCursor: Option[String],
    nextAttemptAt: Option[LocalDateTime],
    lastAttemptedAt: Option[LocalDateTime],
    completedAt: Option[LocalDateTime],
    attemptCount: Int,
    lastFailedAt: Option[LocalDateTime],
    lastErrorType: String,
    lockToken: String,
    lockedUntil: Option[LocalDateTime],
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
