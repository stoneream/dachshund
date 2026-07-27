package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserSessionTokenSource(
    id: Long = 0L,
    userId: Long,
    hashedToken: String,
    issuedAt: BusinessDateTime,
    lastAccessedAt: BusinessDateTime,
    idleExpiresAt: BusinessDateTime,
    expiresAt: BusinessDateTime,
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
