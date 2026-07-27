package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.UserSpotifyAuthorizationRefreshQueueDbRow

object UserSpotifyAuthorizationRefreshQueueDbRowSyntax {

  extension (source: UserSpotifyAuthorizationRefreshQueueSource) {
    def toUserSpotifyAuthorizationRefreshQueueDbRow: UserSpotifyAuthorizationRefreshQueueDbRow = {
      import DbRowValues.*

      UserSpotifyAuthorizationRefreshQueueDbRow(
        id = source.id,
        authorizationId = source.authorizationId,
        status = source.status.dbValue,
        nextAttemptAt = source.nextAttemptAt.dbDateTime,
        attemptCount = source.attemptCount,
        lastFailedAt = source.lastFailedAt.dbDateTime,
        lastErrorType = source.lastErrorType,
        lockToken = source.lockToken,
        lockedUntil = source.lockedUntil.dbDateTime,
        lastAttemptedAt = source.lastAttemptedAt.dbDateTime,
        completedAt = source.completedAt.dbDateTime,
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
