package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus

final case class ArtistReleaseSyncQueueSource(
    id: Long = 0L,
    spotifyArtistCode: String,
    syncScope: String,
    status: QueueJobStatus,
    includeGroups: String,
    market: Option[String],
    requestedLimit: Int,
    nextOffset: Int,
    nextAttemptAt: Option[BusinessDateTime],
    lastAttemptedAt: Option[BusinessDateTime],
    completedAt: Option[BusinessDateTime],
    attemptCount: Int,
    lastFailedAt: Option[BusinessDateTime],
    lastErrorType: String,
    lockToken: String,
    lockedUntil: Option[BusinessDateTime],
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
