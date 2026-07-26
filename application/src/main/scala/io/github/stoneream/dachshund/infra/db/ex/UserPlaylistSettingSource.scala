package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.PlaylistUsageType

final case class UserPlaylistSettingSource(
    userId: Long,
    playlistUsageType: PlaylistUsageType,
    spotifyPlaylistCode: String,
    spotifyPlaylistUri: String,
    playlistName: String,
    enabled: Long,
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
