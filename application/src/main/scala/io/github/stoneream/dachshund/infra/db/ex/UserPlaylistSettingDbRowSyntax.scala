package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.UserPlaylistSettingDbRow

object UserPlaylistSettingDbRowSyntax {

  extension (source: UserPlaylistSettingSource) {
    def toUserPlaylistSettingDbRow: UserPlaylistSettingDbRow = {
      import DbRowValues.*

      UserPlaylistSettingDbRow(
        id = source.id,
        userId = source.userId,
        playlistUsageType = source.playlistUsageType.dbValue,
        spotifyPlaylistCode = source.spotifyPlaylistCode,
        spotifyPlaylistUri = source.spotifyPlaylistUri,
        playlistName = source.playlistName,
        enabled = source.enabled,
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
