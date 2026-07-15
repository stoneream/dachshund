package io.github.stoneream.dachshund.infra.db.reader.access_token

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken
import io.github.stoneream.dachshund.service.spotify.auth.access_token.model.SpotifyAccessTokenResolveTarget
import scalikejdbc.*

import com.google.inject.{Inject, Singleton}

@Singleton
class SpotifyAccessTokenReader @Inject() () {
  def findResolveTargetByUserId(
      userId: Long
  )(using DBSession): Option[SpotifyAccessTokenResolveTarget] =
    sql"""
      select
        usa.id,
        usarq.id as queue_id,
        usa.user_id,
        usa.scope_text,
        usa.access_token_cipher,
        usa.access_token_nonce,
        usa.access_token_tag,
        usa.refresh_token_cipher,
        usa.refresh_token_nonce,
        usa.refresh_token_tag,
        usa.encryption_algorithm,
        usa.encryption_key_version,
        usa.token_type,
        usa.access_token_expires_at,
        usa.refresh_margin_seconds,
        usa.lock_version as authorization_lock_version,
        usarq.status as queue_status,
        usarq.attempt_count,
        usarq.next_attempt_at,
        usarq.last_error_type,
        usarq.lock_version as queue_lock_version
      from
        user_spotify_authorization usa
        inner join user_spotify_authorization_refresh_queue usarq on usarq.authorization_id = usa.id
        inner join user u on u.id = usa.user_id
      where
        usa.user_id = {userId}
        and usa.deleted = 0
        and usarq.deleted = 0
        and u.deleted = 0
        and u.enabled = 1
      limit 1
    """
      .bindByName("userId" -> userId)
      .map { row =>
        SpotifyAccessTokenResolveTarget(
          authorizationId = row.long("id"),
          queueId = row.long("queue_id"),
          userId = row.long("user_id"),
          scopeText = row.string("scope_text"),
          encryptedAccessToken = encryptedAccessToken(row),
          encryptedRefreshToken = encryptedRefreshToken(row),
          tokenType = row.string("token_type"),
          accessTokenExpiresAt = BusinessDateTime.fromLocalDateTime(row.localDateTime("access_token_expires_at")),
          refreshMarginSeconds = row.int("refresh_margin_seconds"),
          attemptCount = row.int("attempt_count"),
          nextAttemptAt = row.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
          lastErrorType = row.stringOpt("last_error_type").map(_.trim).filter(_.nonEmpty),
          queueStatus = row.string("queue_status"),
          authorizationLockVersion = row.long("authorization_lock_version"),
          queueLockVersion = row.long("queue_lock_version")
        )
      }
      .single
      .apply()

  private def encryptedAccessToken(row: WrappedResultSet): EncryptedSpotifyToken =
    encryptedToken(row, "access_token")

  private def encryptedRefreshToken(row: WrappedResultSet): EncryptedSpotifyToken =
    encryptedToken(row, "refresh_token")

  private def encryptedToken(row: WrappedResultSet, columnPrefix: String): EncryptedSpotifyToken =
    EncryptedSpotifyToken(
      cipherText = row.bytes(s"${columnPrefix}_cipher"),
      nonce = row.bytes(s"${columnPrefix}_nonce"),
      tag = row.bytes(s"${columnPrefix}_tag"),
      algorithm = row.string("encryption_algorithm"),
      keyVersion = row.string("encryption_key_version")
    )
}
