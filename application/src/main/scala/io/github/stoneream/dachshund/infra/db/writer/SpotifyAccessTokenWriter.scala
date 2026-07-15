package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

@Singleton
class SpotifyAccessTokenWriter @Inject() () {
  def markRefreshSucceeded(
      authorizationId: Long,
      queueId: Long,
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
      lastRefreshedAt: BusinessDateTime,
      authorizationUpdatedAt: BusinessDateTime,
      authorizationUpdatedUser: AuditUser,
      authorizationLockVersion: Long,
      queueStatus: QueueJobStatus,
      nextAttemptAt: BusinessDateTime,
      attemptCount: Int,
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lastAttemptedAt: BusinessDateTime,
      completedAt: BusinessDateTime,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      queueUpdatedAt: BusinessDateTime,
      queueUpdatedUser: AuditUser,
      queueDeletedAt: Option[BusinessDateTime],
      queueDeletedUser: AuditUser,
      queueDeleted: Long,
      queueLockVersion: Long,
      expectedAuthorizationLockVersion: Long,
      expectedQueueStatus: QueueJobStatus,
      expectedQueueLockVersion: Long
  )(using DBSession): Boolean = {
    val updatedCount = sql"""
      update
        user_spotify_authorization usa
        inner join user_spotify_authorization_refresh_queue usarq
          on usarq.id = {queueId}
          and usarq.authorization_id = usa.id
      set
        usa.scope_text = {scopeText},
        usa.access_token_cipher = {accessTokenCipher},
        usa.access_token_nonce = {accessTokenNonce},
        usa.access_token_tag = {accessTokenTag},
        usa.refresh_token_cipher = {refreshTokenCipher},
        usa.refresh_token_nonce = {refreshTokenNonce},
        usa.refresh_token_tag = {refreshTokenTag},
        usa.encryption_algorithm = {encryptionAlgorithm},
        usa.encryption_key_version = {encryptionKeyVersion},
        usa.token_type = {tokenType},
        usa.access_token_expires_at = {accessTokenExpiresAt},
        usa.last_refreshed_at = {lastRefreshedAt},
        usa.updated_at = {authorizationUpdatedAt},
        usa.updated_user = {authorizationUpdatedUser},
        usa.lock_version = {authorizationLockVersion},
        usarq.status = {queueStatus},
        usarq.next_attempt_at = {nextAttemptAt},
        usarq.attempt_count = {attemptCount},
        usarq.last_failed_at = {lastFailedAt},
        usarq.last_error_type = {lastErrorType},
        usarq.last_attempted_at = {lastAttemptedAt},
        usarq.completed_at = {completedAt},
        usarq.lock_token = {lockToken},
        usarq.locked_until = {lockedUntil},
        usarq.updated_at = {queueUpdatedAt},
        usarq.updated_user = {queueUpdatedUser},
        usarq.deleted_at = {queueDeletedAt},
        usarq.deleted_user = {queueDeletedUser},
        usarq.deleted = {queueDeleted},
        usarq.lock_version = {queueLockVersion}
      where
        usa.id = {authorizationId}
        and usa.lock_version = {expectedAuthorizationLockVersion}
        and usa.deleted = 0
        and usarq.lock_version = {expectedQueueLockVersion}
        and usarq.status = {expectedQueueStatus}
        and usarq.deleted = 0
    """
      .bindByName(
        "authorizationId" -> authorizationId,
        "queueId" -> queueId,
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
        "lastRefreshedAt" -> lastRefreshedAt.toLocalDateTime,
        "authorizationUpdatedAt" -> authorizationUpdatedAt.toLocalDateTime,
        "authorizationUpdatedUser" -> authorizationUpdatedUser.dbValue,
        "authorizationLockVersion" -> authorizationLockVersion,
        "queueStatus" -> queueStatus.dbValue,
        "nextAttemptAt" -> nextAttemptAt.toLocalDateTime,
        "attemptCount" -> attemptCount,
        "lastFailedAt" -> lastFailedAt.map(_.toLocalDateTime),
        "lastErrorType" -> lastErrorType,
        "lastAttemptedAt" -> lastAttemptedAt.toLocalDateTime,
        "completedAt" -> completedAt.toLocalDateTime,
        "lockToken" -> lockToken,
        "lockedUntil" -> lockedUntil.map(_.toLocalDateTime),
        "queueUpdatedAt" -> queueUpdatedAt.toLocalDateTime,
        "queueUpdatedUser" -> queueUpdatedUser.dbValue,
        "queueDeletedAt" -> queueDeletedAt.map(_.toLocalDateTime),
        "queueDeletedUser" -> queueDeletedUser.dbValue,
        "queueDeleted" -> queueDeleted,
        "queueLockVersion" -> queueLockVersion,
        "expectedQueueStatus" -> expectedQueueStatus.dbValue,
        "expectedAuthorizationLockVersion" -> expectedAuthorizationLockVersion,
        "expectedQueueLockVersion" -> expectedQueueLockVersion
      )
      .update
      .apply()

    updatedCount > 0
  }

  def markRefreshFailed(
      authorizationId: Long,
      queueId: Long,
      queueStatus: QueueJobStatus,
      attemptCount: Int,
      nextAttemptAt: Option[BusinessDateTime],
      lastFailedAt: BusinessDateTime,
      lastErrorType: String,
      lastAttemptedAt: BusinessDateTime,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      updatedAt: BusinessDateTime,
      updatedUser: AuditUser,
      lockVersion: Long,
      expectedQueueStatus: QueueJobStatus,
      expectedQueueLockVersion: Long
  )(using DBSession): Boolean = {
    val updatedCount = sql"""
      update
        user_spotify_authorization_refresh_queue usarq
        inner join user_spotify_authorization usa on usa.id = usarq.authorization_id
      set
        usarq.status = {queueStatus},
        usarq.attempt_count = {attemptCount},
        usarq.next_attempt_at = {nextAttemptAt},
        usarq.last_failed_at = {lastFailedAt},
        usarq.last_error_type = {lastErrorType},
        usarq.last_attempted_at = {lastAttemptedAt},
        usarq.lock_token = {lockToken},
        usarq.locked_until = {lockedUntil},
        usarq.updated_at = {updatedAt},
        usarq.updated_user = {updatedUser},
        usarq.lock_version = {lockVersion}
      where
        usarq.id = {queueId}
        and usarq.authorization_id = {authorizationId}
        and usarq.lock_version = {expectedQueueLockVersion}
        and usarq.status = {expectedQueueStatus}
        and usarq.deleted = 0
        and usa.deleted = 0
    """
      .bindByName(
        "authorizationId" -> authorizationId,
        "queueId" -> queueId,
        "queueStatus" -> queueStatus.dbValue,
        "attemptCount" -> attemptCount,
        "nextAttemptAt" -> nextAttemptAt.map(_.toLocalDateTime),
        "lastFailedAt" -> lastFailedAt.toLocalDateTime,
        "lastErrorType" -> lastErrorType,
        "lastAttemptedAt" -> lastAttemptedAt.toLocalDateTime,
        "lockToken" -> lockToken,
        "lockedUntil" -> lockedUntil.map(_.toLocalDateTime),
        "updatedAt" -> updatedAt.toLocalDateTime,
        "updatedUser" -> updatedUser.dbValue,
        "lockVersion" -> lockVersion,
        "expectedQueueStatus" -> expectedQueueStatus.dbValue,
        "expectedQueueLockVersion" -> expectedQueueLockVersion
      )
      .update
      .apply()

    updatedCount > 0
  }
}
