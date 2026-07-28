package io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.model

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.{QueueJobStatus, ReleaseNotificationType}

final case class UserNewReleaseNotificationDeliveryQueueTarget(
    queueId: Long,
    userNewReleaseEventId: Long,
    userId: Long,
    artistReleaseId: Long,
    spotifyReleaseCode: String,
    releaseNotificationType: ReleaseNotificationType,
    playlistSettingId: Long,
    spotifyPlaylistCode: String,
    status: QueueJobStatus,
    nextAttemptAt: Option[BusinessDateTime],
    attemptCount: Int,
    lastFailedAt: Option[BusinessDateTime],
    lastErrorType: String,
    lockToken: String,
    lockedUntil: Option[BusinessDateTime],
    lastAttemptedAt: Option[BusinessDateTime],
    completedAt: Option[BusinessDateTime],
    spotifySnapshotId: String,
    deletedAt: Option[BusinessDateTime],
    deletedUser: AuditUser,
    deleted: Long,
    queueLockVersion: Long
)
