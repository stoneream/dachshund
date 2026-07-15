package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.FollowedArtistSyncQueueDbRow

object FollowedArtistSyncQueueDbRowSyntax {

  extension (source: FollowedArtistSyncQueueSource) {
    def toFollowedArtistSyncQueueDbRow: FollowedArtistSyncQueueDbRow = {
      import DbRowValues.*

      FollowedArtistSyncQueueDbRow(
        id = 0L,
        userId = source.userId,
        syncDate = source.syncDate,
        status = source.status.dbValue,
        requestedLimit = source.requestedLimit,
        afterCursor = source.afterCursor,
        nextAttemptAt = source.nextAttemptAt.dbDateTime,
        lastAttemptedAt = source.lastAttemptedAt.dbDateTime,
        completedAt = source.completedAt.dbDateTime,
        attemptCount = source.attemptCount,
        lastFailedAt = source.lastFailedAt.dbDateTime,
        lastErrorType = source.lastErrorType,
        lockToken = source.lockToken,
        lockedUntil = source.lockedUntil.dbDateTime,
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
