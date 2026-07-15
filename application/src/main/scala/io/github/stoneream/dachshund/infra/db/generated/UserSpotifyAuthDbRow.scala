package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserSpotifyAuthDbRow(
    id: Long,
    userId: Long,
    spotifyUserId: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
