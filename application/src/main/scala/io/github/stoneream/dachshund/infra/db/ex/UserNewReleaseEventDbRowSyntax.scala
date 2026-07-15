package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.UserNewReleaseEventDbRow

object UserNewReleaseEventDbRowSyntax {

  extension (source: UserNewReleaseEventSource) {
    def toUserNewReleaseEventDbRow: UserNewReleaseEventDbRow = {
      import DbRowValues.*

      UserNewReleaseEventDbRow(
        id = 0L,
        userId = source.userId,
        artistReleaseId = source.artistReleaseId,
        spotifyReleaseCode = source.spotifyReleaseCode,
        sourceSpotifyArtistCode = source.sourceSpotifyArtistCode,
        detectedAt = source.detectedAt.dbDateTime,
        detectionSyncCode = source.detectionSyncCode,
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
