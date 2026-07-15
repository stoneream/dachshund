package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserDbRow(
    id: Long,
    userName: String,
    displayName: String,
    timeZone: String,
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
