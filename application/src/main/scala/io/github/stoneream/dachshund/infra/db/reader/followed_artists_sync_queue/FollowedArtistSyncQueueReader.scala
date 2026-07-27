package io.github.stoneream.dachshund.infra.db.reader.followed_artists_sync_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.FollowedArtistSyncQueueSource
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus

import com.google.inject.{Inject, Singleton}
import scalikejdbc.*

import java.time.LocalDate

object FollowedArtistSyncQueueReader {
  final case class ClaimResult(
      target: FollowedArtistSyncQueueSource,
      claimed: Boolean
  )
}

@Singleton
class FollowedArtistSyncQueueReader @Inject() () {
  import FollowedArtistSyncQueueReader.ClaimResult

  def recoverStaleProcessingTargets(
      now: BusinessDateTime
  )(using DBSession): Int =
    sql"""
      update
        followed_artist_sync_queue
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

  def findActiveUserIds()(using DBSession): Seq[Long] =
    sql"""
      select
        u.id
      from
        user u
      where
        u.deleted = 0
        and u.enabled = 1
      order by
        u.id asc
    """
      .map(_.long("id"))
      .list
      .apply()

  def findQueuesForActiveUsers(
      syncDate: LocalDate
  )(using DBSession): Seq[FollowedArtistSyncQueueSource] =
    sql"""
      select
        fasq.id as queue_id,
        fasq.user_id,
        fasq.sync_date,
        fasq.status,
        fasq.requested_limit,
        fasq.after_cursor,
        fasq.next_attempt_at,
        fasq.last_attempted_at,
        fasq.completed_at,
        fasq.attempt_count,
        fasq.last_failed_at,
        fasq.last_error_type,
        fasq.lock_token,
        fasq.locked_until,
        fasq.created_at,
        fasq.updated_at,
        fasq.deleted_at,
        fasq.created_user,
        fasq.updated_user,
        fasq.deleted_user,
        fasq.deleted,
        fasq.lock_version
      from
        followed_artist_sync_queue fasq
        inner join user u on u.id = fasq.user_id
      where
        u.deleted = 0
        and u.enabled = 1
        and fasq.sync_date = {syncDate}
      order by
        fasq.user_id asc
    """
      .bindByName("syncDate" -> syncDate)
      .map(queueTarget)
      .list
      .apply()

  def claimDueTargets(
      now: BusinessDateTime,
      batchSize: Int,
      lockToken: String,
      lockedUntil: BusinessDateTime
  )(using DBSession): Seq[ClaimResult] = {
    val targets = findClaimableTargets(now, batchSize)
    targets.map { target =>
      val claimed = markProcessing(target.id, target.lockVersion, now, lockToken, lockedUntil)
      ClaimResult(
        target =
          if (claimed) {
            target.copy(
              attemptCount = target.attemptCount + 1,
              lockToken = lockToken,
              lockVersion = target.lockVersion + 1L,
              status = QueueJobStatus.Processing,
              lastAttemptedAt = Some(now),
              lockedUntil = Some(lockedUntil),
              updatedAt = now,
              updatedUser = AuditUser.System
            )
          } else {
            target
          },
        claimed = claimed
      )
    }
  }

  private def findClaimableTargets(
      now: BusinessDateTime,
      batchSize: Int
  )(using DBSession): Seq[FollowedArtistSyncQueueSource] =
    sql"""
      select
        fasq.id as queue_id,
        fasq.user_id,
        fasq.sync_date,
        fasq.status,
        fasq.requested_limit,
        fasq.after_cursor,
        fasq.next_attempt_at,
        fasq.last_attempted_at,
        fasq.completed_at,
        fasq.attempt_count,
        fasq.last_failed_at,
        fasq.last_error_type,
        fasq.lock_token,
        fasq.locked_until,
        fasq.created_at,
        fasq.updated_at,
        fasq.deleted_at,
        fasq.created_user,
        fasq.updated_user,
        fasq.deleted_user,
        fasq.deleted,
        fasq.lock_version
      from
        followed_artist_sync_queue fasq
        inner join user u on u.id = fasq.user_id
      where
        fasq.deleted = 0
        and u.deleted = 0
        and u.enabled = 1
        and fasq.status = {status}
        and fasq.next_attempt_at <= {now}
      order by
        fasq.next_attempt_at asc,
        fasq.id asc
      limit {batchSize}
      for update skip locked
    """
      .bindByName(
        "now" -> now.toLocalDateTime,
        "status" -> QueueJobStatus.Scheduled.dbValue,
        "batchSize" -> batchSize
      )
      .map(queueTarget)
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
        followed_artist_sync_queue
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

  private def queueTarget(rs: WrappedResultSet): FollowedArtistSyncQueueSource =
    FollowedArtistSyncQueueSource(
      id = rs.long("queue_id"),
      userId = rs.long("user_id"),
      syncDate = rs.localDate("sync_date"),
      status = QueueJobStatus.fromDbValue(rs.string("status")),
      requestedLimit = rs.int("requested_limit"),
      afterCursor = rs.stringOpt("after_cursor"),
      nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
      lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
      completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
      attemptCount = rs.int("attempt_count"),
      lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
      lastErrorType = rs.string("last_error_type"),
      lockToken = rs.string("lock_token"),
      lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
      createdAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("created_at")),
      updatedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("updated_at")),
      deletedAt = rs.localDateTimeOpt("deleted_at").map(BusinessDateTime.fromLocalDateTime),
      createdUser = AuditUser.fromDbValue(rs.string("created_user")),
      updatedUser = AuditUser.fromDbValue(rs.string("updated_user")),
      deletedUser = AuditUser.fromDbValue(rs.string("deleted_user")),
      deleted = rs.long("deleted"),
      lockVersion = rs.long("lock_version")
    )
}
