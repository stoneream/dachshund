package io.github.stoneream.dachshund.infra.db.reader.artist_release_sync_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseSyncQueueSource
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus

import com.google.inject.{Inject, Singleton}
import scalikejdbc.*

object ArtistReleaseSyncQueueReader {
  final case class ClaimResult(
      target: ArtistReleaseSyncQueueSource,
      claimed: Boolean
  )
}

@Singleton
class ArtistReleaseSyncQueueReader @Inject() () {
  import ArtistReleaseSyncQueueReader.ClaimResult

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
  )(using DBSession): Seq[ArtistReleaseSyncQueueSource] =
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
        arsq.created_at,
        arsq.updated_at,
        arsq.deleted_at,
        arsq.created_user,
        arsq.updated_user,
        arsq.deleted_user,
        arsq.deleted,
        arsq.lock_version
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
  )(using DBSession): Seq[ArtistReleaseSyncQueueSource] =
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
        arsq.created_at,
        arsq.updated_at,
        arsq.deleted_at,
        arsq.created_user,
        arsq.updated_user,
        arsq.deleted_user,
        arsq.deleted,
        arsq.lock_version
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

  private def queueTarget(rs: WrappedResultSet): ArtistReleaseSyncQueueSource =
    ArtistReleaseSyncQueueSource(
      id = rs.long("queue_id"),
      spotifyArtistCode = rs.string("spotify_artist_code"),
      syncScope = rs.string("sync_scope"),
      status = QueueJobStatus.fromDbValue(rs.string("status")),
      includeGroups = rs.string("include_groups"),
      market = rs.stringOpt("market"),
      requestedLimit = rs.int("requested_limit"),
      nextOffset = rs.int("next_offset"),
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
