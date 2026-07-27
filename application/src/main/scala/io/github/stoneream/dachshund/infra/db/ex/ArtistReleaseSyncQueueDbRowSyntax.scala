package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.ArtistReleaseSyncQueueDbRow

object ArtistReleaseSyncQueueDbRowSyntax {

  extension (source: ArtistReleaseSyncQueueSource) {
    def toArtistReleaseSyncQueueDbRow: ArtistReleaseSyncQueueDbRow = {
      import DbRowValues.*

      ArtistReleaseSyncQueueDbRow(
        id = source.id,
        spotifyArtistCode = source.spotifyArtistCode,
        syncScope = source.syncScope,
        status = source.status.dbValue,
        includeGroups = source.includeGroups,
        market = source.market,
        requestedLimit = source.requestedLimit,
        nextOffset = source.nextOffset,
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
