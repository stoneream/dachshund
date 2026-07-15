package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserSessionTokenDbRow(
    id: Long,
    userId: Long,
    hashedToken: String,
    issuedAt: LocalDateTime,
    lastAccessedAt: LocalDateTime,
    idleExpiresAt: LocalDateTime,
    expiresAt: LocalDateTime,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
