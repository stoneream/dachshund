package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserSpotifyAuthorizationDbRow(
    id: Long,
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
    accessTokenExpiresAt: LocalDateTime,
    refreshMarginSeconds: Int,
    lastAuthorizedAt: Option[LocalDateTime],
    lastRefreshedAt: Option[LocalDateTime],
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
