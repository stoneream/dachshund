package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserSessionTokenDbRow
import io.github.stoneream.dachshund.lib.auth.SessionTokenService.IssuedSessionToken
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

object UserSessionTokenDbRowSyntax {

  extension (source: UserSessionTokenSource) {
    def toUserSessionTokenDbRow: UserSessionTokenDbRow = {
      import DbRowValues.*

      UserSessionTokenDbRow(
        id = source.id,
        userId = source.userId,
        hashedToken = source.hashedToken,
        issuedAt = source.issuedAt.dbDateTime,
        lastAccessedAt = source.lastAccessedAt.dbDateTime,
        idleExpiresAt = source.idleExpiresAt.dbDateTime,
        expiresAt = source.expiresAt.dbDateTime,
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

  extension (token: IssuedSessionToken) {
    def toUserSessionTokenDbRow(now: BusinessDateTime): UserSessionTokenDbRow =
      UserSessionTokenSource(
        userId = token.userId,
        hashedToken = token.hashedToken,
        issuedAt = token.issuedAt,
        lastAccessedAt = token.lastAccessedAt,
        idleExpiresAt = token.idleExpiresAt,
        expiresAt = token.expiresAt,
        createdAt = now,
        updatedAt = now,
        deletedAt = Option.empty,
        createdUser = AuditUser.User(token.userId),
        updatedUser = AuditUser.User(token.userId),
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserSessionTokenDbRow
  }
}
