package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserNewReleaseNotificationDeliveryQueueDbRow(
    id: Long,
    userNewReleaseEventId: Long,
    releaseNotificationType: String,
    playlistSettingId: Long,
    status: String,
    nextAttemptAt: Option[LocalDateTime],
    attemptCount: Int,
    lastFailedAt: Option[LocalDateTime],
    lastErrorType: String,
    lockToken: String,
    lockedUntil: Option[LocalDateTime],
    lastAttemptedAt: Option[LocalDateTime],
    completedAt: Option[LocalDateTime],
    spotifySnapshotId: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
