package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.FollowedArtistSyncQueueDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

import java.time.LocalDate

@Singleton
class FollowedArtistSyncQueueWriter @Inject() () {
  def write(row: FollowedArtistSyncQueueDbRow)(using DBSession): Int =
    sql"""
      insert into followed_artist_sync_queue (
        user_id,
        sync_date,
        status,
        requested_limit,
        after_cursor,
        next_attempt_at,
        last_attempted_at,
        completed_at,
        attempt_count,
        last_failed_at,
        last_error_type,
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
        {userId},
        {syncDate},
        {status},
        {requestedLimit},
        {afterCursor},
        {nextAttemptAt},
        {lastAttemptedAt},
        {completedAt},
        {attemptCount},
        {lastFailedAt},
        {lastErrorType},
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
        "userId" -> row.userId,
        "syncDate" -> row.syncDate,
        "status" -> row.status,
        "requestedLimit" -> row.requestedLimit,
        "afterCursor" -> row.afterCursor,
        "nextAttemptAt" -> row.nextAttemptAt,
        "lastAttemptedAt" -> row.lastAttemptedAt,
        "completedAt" -> row.completedAt,
        "attemptCount" -> row.attemptCount,
        "lastFailedAt" -> row.lastFailedAt,
        "lastErrorType" -> row.lastErrorType,
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
      userId: Long,
      syncDate: LocalDate,
      expectedStatus: QueueJobStatus,
      expectedLockToken: String,
      expectedQueueLockVersion: Long,
      expectedDeleted: Long,
      status: QueueJobStatus,
      requestedLimit: Int,
      afterCursor: Option[String],
      nextAttemptAt: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      attemptCount: Int,
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
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
        followed_artist_sync_queue
      set
        status = {status},
        requested_limit = {requestedLimit},
        after_cursor = {afterCursor},
        next_attempt_at = {nextAttemptAt},
        last_attempted_at = {lastAttemptedAt},
        completed_at = {completedAt},
        attempt_count = {attemptCount},
        last_failed_at = {lastFailedAt},
        last_error_type = {lastErrorType},
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
        and user_id = {userId}
        and sync_date = {syncDate}
        and status = {expectedStatus}
        and lock_token = {expectedLockToken}
        and lock_version = {expectedQueueLockVersion}
        and deleted = {expectedDeleted}
    """
      .bindByName(
        "queueId" -> queueId,
        "userId" -> userId,
        "syncDate" -> syncDate,
        "expectedStatus" -> expectedStatus.dbValue,
        "expectedLockToken" -> expectedLockToken,
        "expectedQueueLockVersion" -> expectedQueueLockVersion,
        "expectedDeleted" -> expectedDeleted,
        "status" -> status.dbValue,
        "requestedLimit" -> requestedLimit,
        "afterCursor" -> afterCursor,
        "nextAttemptAt" -> nextAttemptAt.map(_.toLocalDateTime),
        "lastAttemptedAt" -> lastAttemptedAt.map(_.toLocalDateTime),
        "completedAt" -> completedAt.map(_.toLocalDateTime),
        "attemptCount" -> attemptCount,
        "lastFailedAt" -> lastFailedAt.map(_.toLocalDateTime),
        "lastErrorType" -> lastErrorType,
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
