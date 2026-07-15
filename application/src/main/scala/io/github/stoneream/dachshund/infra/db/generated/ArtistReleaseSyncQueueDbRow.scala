package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class ArtistReleaseSyncQueueDbRow(
    id: Long,
    spotifyArtistCode: String,
    syncScope: String,
    status: String,
    includeGroups: String,
    market: Option[String],
    requestedLimit: Int,
    nextOffset: Int,
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
