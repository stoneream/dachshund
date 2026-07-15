package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserSpotifyAuthorizationRefreshQueueDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

@Singleton
class SpotifyAuthorizationRefreshQueueWriter @Inject() () {
  def write(row: UserSpotifyAuthorizationRefreshQueueDbRow)(using DBSession): Int =
    sql"""
      insert into user_spotify_authorization_refresh_queue (
        authorization_id,
        status,
        next_attempt_at,
        attempt_count,
        last_failed_at,
        last_error_type,
        last_attempted_at,
        completed_at,
        lock_token,
        locked_until,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {authorizationId},
        {status},
        {nextAttemptAt},
        {attemptCount},
        {lastFailedAt},
        {lastErrorType},
        {lastAttemptedAt},
        {completedAt},
        {lockToken},
        {lockedUntil},
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
        "authorizationId" -> row.authorizationId,
        "status" -> row.status,
        "nextAttemptAt" -> row.nextAttemptAt,
        "attemptCount" -> row.attemptCount,
        "lastFailedAt" -> row.lastFailedAt,
        "lastErrorType" -> row.lastErrorType,
        "lastAttemptedAt" -> row.lastAttemptedAt,
        "completedAt" -> row.completedAt,
        "lockToken" -> row.lockToken,
        "lockedUntil" -> row.lockedUntil,
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
      queueId: Long,
      authorizationId: Long,
      expectedStatus: QueueJobStatus,
      expectedLockToken: String,
      expectedQueueLockVersion: Long,
      expectedDeleted: Long,
      status: QueueJobStatus,
      nextAttemptAt: Option[BusinessDateTime],
      attemptCount: Int,
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      updatedAt: BusinessDateTime,
      deletedAt: Option[BusinessDateTime],
      updatedUser: AuditUser,
      deletedUser: AuditUser,
      deleted: Long,
      lockVersion: Long
  )(using DBSession): Boolean =
    sql"""
      update
        user_spotify_authorization_refresh_queue
      set
        status = {status},
        next_attempt_at = {nextAttemptAt},
        attempt_count = {attemptCount},
        last_failed_at = {lastFailedAt},
        last_error_type = {lastErrorType},
        last_attempted_at = {lastAttemptedAt},
        completed_at = {completedAt},
        lock_token = {lockToken},
        locked_until = {lockedUntil},
        updated_at = {updatedAt},
        deleted_at = {deletedAt},
        updated_user = {updatedUser},
        deleted_user = {deletedUser},
        deleted = {deleted},
        lock_version = {lockVersion}
      where
        id = {queueId}
        and authorization_id = {authorizationId}
        and status = {expectedStatus}
        and lock_token = {expectedLockToken}
        and lock_version = {expectedQueueLockVersion}
        and deleted = {expectedDeleted}
    """
      .bindByName(
        "queueId" -> queueId,
        "authorizationId" -> authorizationId,
        "expectedStatus" -> expectedStatus.dbValue,
        "expectedLockToken" -> expectedLockToken,
        "expectedQueueLockVersion" -> expectedQueueLockVersion,
        "expectedDeleted" -> expectedDeleted,
        "status" -> status.dbValue,
        "nextAttemptAt" -> nextAttemptAt.map(_.toLocalDateTime),
        "attemptCount" -> attemptCount,
        "lastFailedAt" -> lastFailedAt.map(_.toLocalDateTime),
        "lastErrorType" -> lastErrorType,
        "lastAttemptedAt" -> lastAttemptedAt.map(_.toLocalDateTime),
        "completedAt" -> completedAt.map(_.toLocalDateTime),
        "lockToken" -> lockToken,
        "lockedUntil" -> lockedUntil.map(_.toLocalDateTime),
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
