package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.generated.UserSpotifyAuthorizationDbRow

object UserSpotifyAuthorizationDbRowSyntax {

  extension (source: UserSpotifyAuthorizationSource) {
    def toUserSpotifyAuthorizationDbRow: UserSpotifyAuthorizationDbRow = {
      import DbRowValues.*

      UserSpotifyAuthorizationDbRow(
        id = 0L,
        userId = source.userId,
        scopeText = source.scopeText,
        accessTokenCipher = source.accessTokenCipher,
        accessTokenNonce = source.accessTokenNonce,
        accessTokenTag = source.accessTokenTag,
        refreshTokenCipher = source.refreshTokenCipher,
        refreshTokenNonce = source.refreshTokenNonce,
        refreshTokenTag = source.refreshTokenTag,
        encryptionAlgorithm = source.encryptionAlgorithm,
        encryptionKeyVersion = source.encryptionKeyVersion,
        tokenType = source.tokenType,
        accessTokenExpiresAt = source.accessTokenExpiresAt.dbDateTime,
        refreshMarginSeconds = source.refreshMarginSeconds,
        lastAuthorizedAt = source.lastAuthorizedAt.dbDateTime,
        lastRefreshedAt = source.lastRefreshedAt.dbDateTime,
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
