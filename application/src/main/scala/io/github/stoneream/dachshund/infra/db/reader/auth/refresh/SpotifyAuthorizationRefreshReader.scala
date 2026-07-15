package io.github.stoneream.dachshund.infra.db.reader.auth.refresh

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken
import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

import com.google.inject.{Inject, Singleton}

object SpotifyAuthorizationRefreshReader {
  final case class RefreshTargetRow(
      authorizationId: Long,
      queueId: Long,
      userId: Long,
      scopeText: String,
      encryptedRefreshToken: EncryptedSpotifyToken,
      tokenType: String,
      accessTokenExpiresAt: BusinessDateTime,
      refreshMarginSeconds: Int,
      lastAuthorizedAt: Option[BusinessDateTime],
      lastRefreshedAt: Option[BusinessDateTime],
      queueStatus: QueueJobStatus,
      attemptCount: Int,
      nextAttemptAt: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      authorizationLockVersion: Long,
      queueLockVersion: Long,
      queueDeleted: Long
  )

  final case class ClaimResult(
      target: RefreshTargetRow,
      claimed: Boolean
  )
}

@Singleton
class SpotifyAuthorizationRefreshReader @Inject() () {
  import SpotifyAuthorizationRefreshReader.{ClaimResult, RefreshTargetRow}

  def recoverStaleProcessingTargets(
      now: BusinessDateTime
  )(using DBSession): Int =
    sql"""
      update
        user_spotify_authorization_refresh_queue
      set
        status = {scheduledStatus},
        lock_token = '',
        locked_until = null,
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        lock_version = lock_version + 1
      where
        status = {processingStatus}
        and locked_until is not null
        and locked_until <= {now}
        and deleted = 0
    """
      .bindByName(
        "scheduledStatus" -> QueueJobStatus.Scheduled.dbValue,
        "processingStatus" -> QueueJobStatus.Processing.dbValue,
        "now" -> now.toLocalDateTime,
        "updatedAt" -> now.toLocalDateTime,
        "updatedUser" -> AuditUser.System.dbValue
      )
      .update
      .apply()

  def claimRefreshTargets(
      now: BusinessDateTime,
      batchSize: Int,
      lockToken: String,
      lockedUntil: BusinessDateTime
  )(using DBSession): Seq[ClaimResult] = {
    val targets = findClaimableRefreshTargets(now, batchSize)
    targets.map { target =>
      val claimed = markProcessing(target.queueId, target.queueLockVersion, now, lockToken, lockedUntil)
      ClaimResult(
        target =
          if (claimed) {
            target.copy(
              queueStatus = QueueJobStatus.Processing,
              attemptCount = target.attemptCount + 1,
              lastAttemptedAt = Some(now),
              lockToken = lockToken,
              lockedUntil = Some(lockedUntil),
              queueLockVersion = target.queueLockVersion + 1L
            )
          } else {
            target
          },
        claimed = claimed
      )
    }
  }

  private def findClaimableRefreshTargets(
      now: BusinessDateTime,
      batchSize: Int
  )(using DBSession): Seq[RefreshTargetRow] =
    sql"""
      select
        usa.id,
        usarq.id as queue_id,
        usa.user_id,
        usa.scope_text,
        usa.refresh_token_cipher,
        usa.refresh_token_nonce,
        usa.refresh_token_tag,
        usa.encryption_algorithm,
        usa.encryption_key_version,
        usa.token_type,
        usa.access_token_expires_at,
        usa.refresh_margin_seconds,
        usa.last_authorized_at,
        usa.last_refreshed_at,
        usarq.status,
        usarq.attempt_count,
        usarq.next_attempt_at,
        usarq.last_attempted_at,
        usarq.completed_at,
        usarq.last_failed_at,
        usarq.last_error_type,
        usarq.lock_token,
        usarq.locked_until,
        usa.lock_version as authorization_lock_version,
        usarq.lock_version as queue_lock_version,
        usarq.deleted as queue_deleted
      from
        user_spotify_authorization usa
        inner join user_spotify_authorization_refresh_queue usarq on usarq.authorization_id = usa.id
        inner join user u on u.id = usa.user_id
      where
        usa.deleted = 0
        and usarq.deleted = 0
        and u.deleted = 0
        and u.enabled = 1
        and usarq.status = {status}
        and usarq.next_attempt_at <= {now}
      order by
        usarq.next_attempt_at asc,
        usarq.id asc
      limit {batchSize}
      for update skip locked
    """
      .bindByName(
        "now" -> now.toLocalDateTime,
        "status" -> QueueJobStatus.Scheduled.dbValue,
        "batchSize" -> batchSize
      )
      .map { row =>
        RefreshTargetRow(
          authorizationId = row.long("id"),
          queueId = row.long("queue_id"),
          userId = row.long("user_id"),
          scopeText = row.string("scope_text"),
          encryptedRefreshToken = encryptedRefreshToken(row),
          tokenType = row.string("token_type"),
          accessTokenExpiresAt = BusinessDateTime.fromLocalDateTime(row.localDateTime("access_token_expires_at")),
          refreshMarginSeconds = row.int("refresh_margin_seconds"),
          lastAuthorizedAt = row.localDateTimeOpt("last_authorized_at").map(BusinessDateTime.fromLocalDateTime),
          lastRefreshedAt = row.localDateTimeOpt("last_refreshed_at").map(BusinessDateTime.fromLocalDateTime),
          queueStatus = QueueJobStatus.fromDbValue(row.string("status")),
          attemptCount = row.int("attempt_count"),
          nextAttemptAt = row.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
          lastAttemptedAt = row.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
          completedAt = row.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
          lastFailedAt = row.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
          lastErrorType = row.string("last_error_type"),
          lockToken = row.string("lock_token"),
          lockedUntil = row.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
          authorizationLockVersion = row.long("authorization_lock_version"),
          queueLockVersion = row.long("queue_lock_version"),
          queueDeleted = row.long("queue_deleted")
        )
      }
      .list
      .apply()

  private def markProcessing(
      queueId: Long,
      expectedQueueLockVersion: Long,
      now: BusinessDateTime,
      lockToken: String,
      lockedUntil: BusinessDateTime
  )(using DBSession): Boolean =
    sql"""
      update
        user_spotify_authorization_refresh_queue
      set
        status = {status},
        attempt_count = attempt_count + 1,
        last_attempted_at = {lastAttemptedAt},
        lock_token = {lockToken},
        locked_until = {lockedUntil},
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        lock_version = lock_version + 1
      where
        id = {queueId}
        and status = {scheduledStatus}
        and lock_version = {expectedQueueLockVersion}
        and deleted = 0
    """
      .bindByName(
        "queueId" -> queueId,
        "status" -> QueueJobStatus.Processing.dbValue,
        "scheduledStatus" -> QueueJobStatus.Scheduled.dbValue,
        "expectedQueueLockVersion" -> expectedQueueLockVersion,
        "lastAttemptedAt" -> now.toLocalDateTime,
        "lockToken" -> lockToken,
        "lockedUntil" -> lockedUntil.toLocalDateTime,
        "updatedAt" -> now.toLocalDateTime,
        "updatedUser" -> AuditUser.System.dbValue
      )
      .update
      .apply() == 1

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
