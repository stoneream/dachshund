package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserPlaylistSettingDbRow(
    id: Long,
    userId: Long,
    playlistUsageType: String,
    spotifyPlaylistCode: String,
    spotifyPlaylistUri: String,
    playlistName: String,
    enabled: Long,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
