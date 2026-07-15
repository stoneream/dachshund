package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserSpotifyAuthorizationDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import scalikejdbc.*

@Singleton
class SpotifyAuthorizationWriter @Inject() () {
  def write(row: UserSpotifyAuthorizationDbRow)(using DBSession): Int =
    sql"""
      insert into user_spotify_authorization (
        user_id,
        scope_text,
        access_token_cipher,
        access_token_nonce,
        access_token_tag,
        refresh_token_cipher,
        refresh_token_nonce,
        refresh_token_tag,
        encryption_algorithm,
        encryption_key_version,
        token_type,
        access_token_expires_at,
        refresh_margin_seconds,
        last_authorized_at,
        last_refreshed_at,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {userId},
        {scopeText},
        {accessTokenCipher},
        {accessTokenNonce},
        {accessTokenTag},
        {refreshTokenCipher},
        {refreshTokenNonce},
        {refreshTokenTag},
        {encryptionAlgorithm},
        {encryptionKeyVersion},
        {tokenType},
        {accessTokenExpiresAt},
        {refreshMarginSeconds},
        {lastAuthorizedAt},
        {lastRefreshedAt},
        {createdAt},
        {updatedAt},
        {deletedAt},
        {createdUser},
        {updatedUser},
        {deletedUser},
        {deleted},
        {lockVersion}
      )
    """
      .bindByName(
        "userId" -> row.userId,
        "scopeText" -> row.scopeText,
        "accessTokenCipher" -> row.accessTokenCipher,
        "accessTokenNonce" -> row.accessTokenNonce,
        "accessTokenTag" -> row.accessTokenTag,
        "refreshTokenCipher" -> row.refreshTokenCipher,
        "refreshTokenNonce" -> row.refreshTokenNonce,
        "refreshTokenTag" -> row.refreshTokenTag,
        "encryptionAlgorithm" -> row.encryptionAlgorithm,
        "encryptionKeyVersion" -> row.encryptionKeyVersion,
        "tokenType" -> row.tokenType,
        "accessTokenExpiresAt" -> row.accessTokenExpiresAt,
        "refreshMarginSeconds" -> row.refreshMarginSeconds,
        "lastAuthorizedAt" -> row.lastAuthorizedAt,
        "lastRefreshedAt" -> row.lastRefreshedAt,
        "createdAt" -> row.createdAt,
        "updatedAt" -> row.updatedAt,
        "deletedAt" -> row.deletedAt,
        "createdUser" -> row.createdUser,
        "updatedUser" -> row.updatedUser,
        "deletedUser" -> row.deletedUser,
        "deleted" -> row.deleted,
        "lockVersion" -> row.lockVersion
      )
      .update
      .apply()

  def update(
      authorizationId: Long,
      userId: Long,
      expectedLockVersion: Long,
      expectedDeleted: Long,
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
      updatedAt: BusinessDateTime,
      deletedAt: Option[BusinessDateTime],
      updatedUser: AuditUser,
      deletedUser: AuditUser,
      deleted: Long,
      lockVersion: Long
  )(using DBSession): Boolean =
    sql"""
      update
        user_spotify_authorization
      set
        scope_text = {scopeText},
        access_token_cipher = {accessTokenCipher},
        access_token_nonce = {accessTokenNonce},
        access_token_tag = {accessTokenTag},
        refresh_token_cipher = {refreshTokenCipher},
        refresh_token_nonce = {refreshTokenNonce},
        refresh_token_tag = {refreshTokenTag},
        encryption_algorithm = {encryptionAlgorithm},
        encryption_key_version = {encryptionKeyVersion},
        token_type = {tokenType},
        access_token_expires_at = {accessTokenExpiresAt},
        refresh_margin_seconds = {refreshMarginSeconds},
        last_authorized_at = {lastAuthorizedAt},
        last_refreshed_at = {lastRefreshedAt},
        updated_at = {updatedAt},
        deleted_at = {deletedAt},
        updated_user = {updatedUser},
        deleted_user = {deletedUser},
        deleted = {deleted},
        lock_version = {lockVersion}
      where
        id = {authorizationId}
        and user_id = {userId}
        and lock_version = {expectedLockVersion}
        and deleted = {expectedDeleted}
    """
      .bindByName(
        "authorizationId" -> authorizationId,
        "userId" -> userId,
        "expectedLockVersion" -> expectedLockVersion,
        "expectedDeleted" -> expectedDeleted,
        "scopeText" -> scopeText,
        "accessTokenCipher" -> accessTokenCipher,
        "accessTokenNonce" -> accessTokenNonce,
        "accessTokenTag" -> accessTokenTag,
        "refreshTokenCipher" -> refreshTokenCipher,
        "refreshTokenNonce" -> refreshTokenNonce,
        "refreshTokenTag" -> refreshTokenTag,
        "encryptionAlgorithm" -> encryptionAlgorithm,
        "encryptionKeyVersion" -> encryptionKeyVersion,
        "tokenType" -> tokenType,
        "accessTokenExpiresAt" -> accessTokenExpiresAt.toLocalDateTime,
        "refreshMarginSeconds" -> refreshMarginSeconds,
        "lastAuthorizedAt" -> lastAuthorizedAt.map(_.toLocalDateTime),
        "lastRefreshedAt" -> lastRefreshedAt.map(_.toLocalDateTime),
        "updatedAt" -> updatedAt.toLocalDateTime,
        "deletedAt" -> deletedAt.map(_.toLocalDateTime),
        "updatedUser" -> updatedUser.dbValue,
        "deletedUser" -> deletedUser.dbValue,
        "deleted" -> deleted,
        "lockVersion" -> lockVersion
      )
      .update
      .apply() == 1
}
