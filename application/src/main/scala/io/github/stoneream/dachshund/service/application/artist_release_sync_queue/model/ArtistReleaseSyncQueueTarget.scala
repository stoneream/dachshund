package io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus

final case class ArtistReleaseSyncQueueTarget(
    queueId: Long,
    spotifyArtistCode: String,
    syncScope: String,
    includeGroups: String,
    market: Option[String],
    requestedLimit: Int,
    nextOffset: Int,
    attemptCount: Int,
    lockToken: String,
    queueLockVersion: Long,
    status: QueueJobStatus = QueueJobStatus.Scheduled,
    nextAttemptAt: Option[BusinessDateTime] = None,
    lastAttemptedAt: Option[BusinessDateTime] = None,
    completedAt: Option[BusinessDateTime] = None,
    lastFailedAt: Option[BusinessDateTime] = None,
    lastErrorType: String = "",
    lockedUntil: Option[BusinessDateTime] = None,
    deletedAt: Option[BusinessDateTime] = None,
    deletedUser: AuditUser = AuditUser.Empty,
    deleted: Long = 0L
)
