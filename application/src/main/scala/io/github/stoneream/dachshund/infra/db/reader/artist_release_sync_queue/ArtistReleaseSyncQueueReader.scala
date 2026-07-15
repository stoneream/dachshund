package io.github.stoneream.dachshund.infra.db.reader.artist_release_sync_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.{ArtistReleaseSyncQueueClaimResult, ArtistReleaseSyncQueueTarget}
import scalikejdbc.*

@Singleton
class ArtistReleaseSyncQueueReader @Inject() () {
  def recoverStaleProcessingTargets(
      now: BusinessDateTime
  )(using DBSession): Int =
    sql"""
      update
        artist_release_sync_queue
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

  def findActiveFollowedArtistCodes()(using DBSession): Seq[String] =
    sql"""
      select distinct
        ufa.spotify_artist_code
      from
        user_followed_artist ufa
        inner join user u on u.id = ufa.user_id
      where
        ufa.deleted = 0
        and u.deleted = 0
        and u.enabled = 1
      order by
        ufa.spotify_artist_code asc
    """
      .map(_.string("spotify_artist_code"))
      .list
      .apply()

  def findQueuesForActiveFollowedArtists(
      syncScope: String
  )(using DBSession): Seq[ArtistReleaseSyncQueueTarget] =
    sql"""
      select
        arsq.id as queue_id,
        arsq.spotify_artist_code,
        arsq.sync_scope,
        arsq.status,
        arsq.include_groups,
        arsq.market,
        arsq.requested_limit,
        arsq.next_offset,
        arsq.next_attempt_at,
        arsq.last_attempted_at,
        arsq.completed_at,
        arsq.attempt_count,
        arsq.last_failed_at,
        arsq.last_error_type,
        arsq.lock_token,
        arsq.locked_until,
        arsq.deleted_at,
        arsq.deleted,
        arsq.lock_version as queue_lock_version
      from
        artist_release_sync_queue arsq
        inner join (
          select distinct
            ufa.spotify_artist_code
          from
            user_followed_artist ufa
            inner join user u on u.id = ufa.user_id
          where
            ufa.deleted = 0
            and u.deleted = 0
            and u.enabled = 1
        ) target on target.spotify_artist_code = arsq.spotify_artist_code
      where
        arsq.sync_scope = {syncScope}
      order by
        arsq.spotify_artist_code asc
    """
      .bindByName("syncScope" -> syncScope)
      .map(queueTarget)
      .list
      .apply()

  def claimDueTargets(
      now: BusinessDateTime,
      batchSize: Int,
      lockToken: String,
      lockedUntil: BusinessDateTime
  )(using DBSession): Seq[ArtistReleaseSyncQueueClaimResult] = {
    val targets = findClaimableTargets(now, batchSize)
    targets.map { target =>
      val claimed = markProcessing(target.queueId, target.queueLockVersion, now, lockToken, lockedUntil)
      ArtistReleaseSyncQueueClaimResult(
        target =
          if (claimed) {
            target.copy(
              attemptCount = target.attemptCount + 1,
              lockToken = lockToken,
              queueLockVersion = target.queueLockVersion + 1L,
              status = QueueJobStatus.Processing,
              lastAttemptedAt = Some(now),
              lockedUntil = Some(lockedUntil)
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
  )(using DBSession): Seq[ArtistReleaseSyncQueueTarget] =
    sql"""
      select
        arsq.id as queue_id,
        arsq.spotify_artist_code,
        arsq.sync_scope,
        arsq.status,
        arsq.include_groups,
        arsq.market,
        arsq.requested_limit,
        arsq.next_offset,
        arsq.next_attempt_at,
        arsq.last_attempted_at,
        arsq.completed_at,
        arsq.attempt_count,
        arsq.last_failed_at,
        arsq.last_error_type,
        arsq.lock_token,
        arsq.locked_until,
        arsq.deleted_at,
        arsq.deleted,
        arsq.lock_version as queue_lock_version
      from
        artist_release_sync_queue arsq
        inner join (
          select distinct
            ufa.spotify_artist_code
          from
            user_followed_artist ufa
            inner join user u on u.id = ufa.user_id
          where
            ufa.deleted = 0
            and u.deleted = 0
            and u.enabled = 1
        ) target on target.spotify_artist_code = arsq.spotify_artist_code
      where
        arsq.deleted = 0
        and arsq.status = {status}
        and arsq.next_attempt_at <= {now}
      order by
        arsq.next_attempt_at asc,
        arsq.id asc
      limit {batchSize}
      for update skip locked
    """
      .bindByName(
        "now" -> now.toLocalDateTime,
        "status" -> QueueJobStatus.Scheduled.dbValue,
        "batchSize" -> batchSize
      )
      .map { rs =>
        queueTarget(rs)
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
        artist_release_sync_queue
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

  private def queueTarget(rs: WrappedResultSet): ArtistReleaseSyncQueueTarget =
    ArtistReleaseSyncQueueTarget(
      queueId = rs.long("queue_id"),
      spotifyArtistCode = rs.string("spotify_artist_code"),
      syncScope = rs.string("sync_scope"),
      includeGroups = rs.string("include_groups"),
      market = rs.stringOpt("market"),
      requestedLimit = rs.int("requested_limit"),
      nextOffset = rs.int("next_offset"),
      attemptCount = rs.int("attempt_count"),
      lockToken = rs.string("lock_token"),
      queueLockVersion = rs.long("queue_lock_version"),
      status = QueueJobStatus.fromDbValue(rs.string("status")),
      nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
      lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
      completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
      lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
      lastErrorType = rs.string("last_error_type"),
      lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
      deletedAt = rs.localDateTimeOpt("deleted_at").map(BusinessDateTime.fromLocalDateTime),
      deletedUser = AuditUser.Empty,
      deleted = rs.long("deleted")
    )
}
