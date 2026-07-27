package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserSpotifyAuthorizationSource(
    id: Long = 0L,
    userId: Long,
    scopeText: String,
    accessTokenCipher: Array[Byte],
    accessTokenNonce: Array[Byte],
    accessTokenTag: Array[Byte],
    refreshTokenCipher: Array[Byte],
    refreshTokenNonce: Array[Byte],
    refreshTokenTag: Array[Byte],
    encryptionAlgorithm: String,
    encryptionKeyVersion: String,
    tokenType: String,
    accessTokenExpiresAt: BusinessDateTime,
    refreshMarginSeconds: Int,
    lastAuthorizedAt: Option[BusinessDateTime],
    lastRefreshedAt: Option[BusinessDateTime],
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
