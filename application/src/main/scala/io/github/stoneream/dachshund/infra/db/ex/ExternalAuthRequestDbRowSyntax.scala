package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.ExternalAuthRequestDbRow

object ExternalAuthRequestDbRowSyntax {

  extension (source: ExternalAuthRequestSource) {
    def toExternalAuthRequestDbRow: ExternalAuthRequestDbRow = {
      import DbRowValues.*

      ExternalAuthRequestDbRow(
        id = 0L,
        flowType = source.flowType.dbValue,
        providerType = source.providerType.dbValue,
        state = source.state,
        nonce = source.nonce,
        codeVerifier = source.codeVerifier,
        redirectUri = source.redirectUri,
        scopes = source.scopes,
        status = source.status.dbValue,
        expiresAt = source.expiresAt.dbDateTime,
        completedAt = source.completedAt.dbDateTime,
        errorCode = source.errorCode,
        errorDescription = source.errorDescription,
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
