package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object UserSpotifyAuthorizationTable {
  val Name = "user_spotify_authorization"

  object Columns {
    val Id = "id"
    val UserId = "user_id"
    val ScopeText = "scope_text"
    val AccessTokenCipher = "access_token_cipher"
    val AccessTokenNonce = "access_token_nonce"
    val AccessTokenTag = "access_token_tag"
    val RefreshTokenCipher = "refresh_token_cipher"
    val RefreshTokenNonce = "refresh_token_nonce"
    val RefreshTokenTag = "refresh_token_tag"
    val EncryptionAlgorithm = "encryption_algorithm"
    val EncryptionKeyVersion = "encryption_key_version"
    val TokenType = "token_type"
    val AccessTokenExpiresAt = "access_token_expires_at"
    val RefreshMarginSeconds = "refresh_margin_seconds"
    val LastAuthorizedAt = "last_authorized_at"
    val LastRefreshedAt = "last_refreshed_at"
    val CreatedAt = "created_at"
    val UpdatedAt = "updated_at"
    val DeletedAt = "deleted_at"
    val CreatedUser = "created_user"
    val UpdatedUser = "updated_user"
    val DeletedUser = "deleted_user"
    val Deleted = "deleted"
    val LockVersion = "lock_version"

    val All: Seq[String] = Seq(
      Id,
      UserId,
      ScopeText,
      AccessTokenCipher,
      AccessTokenNonce,
      AccessTokenTag,
      RefreshTokenCipher,
      RefreshTokenNonce,
      RefreshTokenTag,
      EncryptionAlgorithm,
      EncryptionKeyVersion,
      TokenType,
      AccessTokenExpiresAt,
      RefreshMarginSeconds,
      LastAuthorizedAt,
      LastRefreshedAt,
      CreatedAt,
      UpdatedAt,
      DeletedAt,
      CreatedUser,
      UpdatedUser,
      DeletedUser,
      Deleted,
      LockVersion
    )
  }

  val InsertAuditColumnNames: Seq[String] = Seq(Columns.CreatedAt, Columns.CreatedUser)
  val UpdateAuditColumnNames: Seq[String] = Seq(Columns.UpdatedAt, Columns.UpdatedUser, Columns.LockVersion)
  val DeleteAuditColumnNames: Seq[String] = Seq(Columns.DeletedAt, Columns.DeletedUser, Columns.Deleted)

  def map(rs: WrappedResultSet): UserSpotifyAuthorizationDbRow =
    UserSpotifyAuthorizationDbRow(
      id = rs.long(Columns.Id),
      userId = rs.long(Columns.UserId),
      scopeText = rs.string(Columns.ScopeText),
      accessTokenCipher = rs.bytes(Columns.AccessTokenCipher),
      accessTokenNonce = rs.bytes(Columns.AccessTokenNonce),
      accessTokenTag = rs.bytes(Columns.AccessTokenTag),
      refreshTokenCipher = rs.bytes(Columns.RefreshTokenCipher),
      refreshTokenNonce = rs.bytes(Columns.RefreshTokenNonce),
      refreshTokenTag = rs.bytes(Columns.RefreshTokenTag),
      encryptionAlgorithm = rs.string(Columns.EncryptionAlgorithm),
      encryptionKeyVersion = rs.string(Columns.EncryptionKeyVersion),
      tokenType = rs.string(Columns.TokenType),
      accessTokenExpiresAt = rs.localDateTime(Columns.AccessTokenExpiresAt),
      refreshMarginSeconds = rs.int(Columns.RefreshMarginSeconds),
      lastAuthorizedAt = rs.localDateTimeOpt(Columns.LastAuthorizedAt),
      lastRefreshedAt = rs.localDateTimeOpt(Columns.LastRefreshedAt),
      createdAt = rs.localDateTime(Columns.CreatedAt),
      updatedAt = rs.localDateTime(Columns.UpdatedAt),
      deletedAt = rs.localDateTimeOpt(Columns.DeletedAt),
      createdUser = rs.string(Columns.CreatedUser),
      updatedUser = rs.string(Columns.UpdatedUser),
      deletedUser = rs.string(Columns.DeletedUser),
      deleted = rs.long(Columns.Deleted),
      lockVersion = rs.long(Columns.LockVersion)
    )
}
