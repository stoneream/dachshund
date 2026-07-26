package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.UserNewReleaseNotificationQueueDbRow

object UserNewReleaseNotificationQueueDbRowSyntax {

  extension (source: UserNewReleaseNotificationQueueSource) {
    def toUserNewReleaseNotificationQueueDbRow: UserNewReleaseNotificationQueueDbRow = {
      import DbRowValues.*

      UserNewReleaseNotificationQueueDbRow(
        id = 0L,
        userNewReleaseEventId = source.userNewReleaseEventId,
        releaseNotificationType = source.releaseNotificationType.dbValue,
        playlistSettingId = source.playlistSettingId,
        status = source.status.dbValue,
        nextAttemptAt = source.nextAttemptAt.dbDateTime,
        attemptCount = source.attemptCount,
        lastFailedAt = source.lastFailedAt.dbDateTime,
        lastErrorType = source.lastErrorType,
        lockToken = source.lockToken,
        lockedUntil = source.lockedUntil.dbDateTime,
        lastAttemptedAt = source.lastAttemptedAt.dbDateTime,
        completedAt = source.completedAt.dbDateTime,
        spotifySnapshotId = source.spotifySnapshotId,
        createdAt = source.createdAt.dbDateTime,
        updatedAt = source.updatedAt.dbDateTime,
        deletedAt = source.deletedAt.dbDateTime,
        createdUser = source.createdUser.dbAuditUser,
        updatedUser = source.updatedUser.dbAuditUser,
        deletedUser = source.deletedUser.dbAuditUser,
        deleted = source.deleted,
        lockVersion = source.lockVersion
      )
    }
  }
}
