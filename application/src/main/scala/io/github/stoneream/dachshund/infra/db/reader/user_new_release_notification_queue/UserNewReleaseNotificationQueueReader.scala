package io.github.stoneream.dachshund.infra.db.reader.user_new_release_notification_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseNotificationQueueSource
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.{QueueJobStatus, ReleaseNotificationType}

import com.google.inject.{Inject, Singleton}
import scalikejdbc.*

object UserNewReleaseNotificationQueueReader {
  final case class QueueTarget(
      queue: UserNewReleaseNotificationQueueSource,
      userId: Long,
      artistReleaseId: Long,
      spotifyReleaseCode: String,
      spotifyPlaylistCode: String
  )

  final case class ClaimResult(
      target: QueueTarget,
      claimed: Boolean
  )
}

@Singleton
class UserNewReleaseNotificationQueueReader @Inject() () {
  import UserNewReleaseNotificationQueueReader.{ClaimResult, QueueTarget}

  def recoverStaleProcessingTargets(
      now: BusinessDateTime,
      releaseNotificationType: ReleaseNotificationType
  )(using DBSession): Int =
    sql"""
      update
        user_new_release_notification_queue
      set
        status = {scheduledStatus},
        lock_token = '',
        locked_until = null,
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        lock_version = lock_version + 1
      where
        status = {processingStatus}
        and release_notification_type = {releaseNotificationType}
        and locked_until is not null
        and locked_until <= {now}
        and deleted = 0
    """
      .bindByName(
        "scheduledStatus" -> QueueJobStatus.Scheduled.dbValue,
        "processingStatus" -> QueueJobStatus.Processing.dbValue,
        "releaseNotificationType" -> releaseNotificationType.dbValue,
        "now" -> now.toLocalDateTime,
        "updatedAt" -> now.toLocalDateTime,
        "updatedUser" -> AuditUser.System.dbValue
      )
      .update
      .apply()

  def claimDueTargets(
      now: BusinessDateTime,
      releaseNotificationType: ReleaseNotificationType,
      batchSize: Int,
      lockToken: String,
      lockedUntil: BusinessDateTime
  )(using DBSession): Seq[ClaimResult] = {
    val targets = findClaimableTargets(now, releaseNotificationType, batchSize)
    targets.map { target =>
      val claimed = markProcessing(target.queue.id, target.queue.lockVersion, now, lockToken, lockedUntil)
      ClaimResult(
        target =
          if (claimed) {
            target.copy(
              queue = target.queue.copy(
                attemptCount = target.queue.attemptCount + 1,
                lockToken = lockToken,
                lockVersion = target.queue.lockVersion + 1L,
                status = QueueJobStatus.Processing,
                lastAttemptedAt = Some(now),
                lockedUntil = Some(lockedUntil),
                updatedAt = now,
                updatedUser = AuditUser.System
              )
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
      releaseNotificationType: ReleaseNotificationType,
      batchSize: Int
  )(using DBSession): Seq[QueueTarget] =
    sql"""
      select
        q.id as queue_id,
        q.user_new_release_event_id,
        une.user_id,
        une.artist_release_id,
        une.spotify_release_code,
        q.release_notification_type,
        q.playlist_setting_id,
        ups.spotify_playlist_code,
        q.status,
        q.next_attempt_at,
        q.attempt_count,
        q.last_failed_at,
        q.last_error_type,
        q.lock_token,
        q.locked_until,
        q.last_attempted_at,
        q.completed_at,
        q.spotify_snapshot_id,
        q.created_at,
        q.updated_at,
        q.deleted_at,
        q.created_user,
        q.updated_user,
        q.deleted_user,
        q.deleted,
        q.lock_version
      from
        user_new_release_notification_queue q
        inner join user_new_release_event une
          on une.id = q.user_new_release_event_id
          and une.deleted = 0
        inner join user u
          on u.id = une.user_id
          and u.deleted = 0
          and u.enabled = 1
        inner join user_playlist_setting ups
          on ups.id = q.playlist_setting_id
          and ups.deleted = 0
          and ups.enabled = 1
      where
        q.deleted = 0
        and q.status = {status}
        and q.release_notification_type = {releaseNotificationType}
        and q.next_attempt_at <= {now}
      order by
        q.next_attempt_at asc,
        q.id asc
      limit {batchSize}
      for update skip locked
    """
      .bindByName(
        "now" -> now.toLocalDateTime,
        "status" -> QueueJobStatus.Scheduled.dbValue,
        "releaseNotificationType" -> releaseNotificationType.dbValue,
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
        user_new_release_notification_queue
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

  private def queueTarget(rs: WrappedResultSet): QueueTarget =
    QueueTarget(
      queue = UserNewReleaseNotificationQueueSource(
        id = rs.long("queue_id"),
        userNewReleaseEventId = rs.long("user_new_release_event_id"),
        releaseNotificationType = ReleaseNotificationType.fromDbValue(rs.string("release_notification_type")),
        playlistSettingId = rs.long("playlist_setting_id"),
        status = QueueJobStatus.fromDbValue(rs.string("status")),
        nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
        attemptCount = rs.int("attempt_count"),
        lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
        lastErrorType = rs.string("last_error_type"),
        lockToken = rs.string("lock_token"),
        lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
        lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
        completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
        spotifySnapshotId = rs.string("spotify_snapshot_id"),
        createdAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("created_at")),
        updatedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("updated_at")),
        deletedAt = rs.localDateTimeOpt("deleted_at").map(BusinessDateTime.fromLocalDateTime),
        createdUser = AuditUser.fromDbValue(rs.string("created_user")),
        updatedUser = AuditUser.fromDbValue(rs.string("updated_user")),
        deletedUser = AuditUser.fromDbValue(rs.string("deleted_user")),
        deleted = rs.long("deleted"),
        lockVersion = rs.long("lock_version")
      ),
      userId = rs.long("user_id"),
      artistReleaseId = rs.long("artist_release_id"),
      spotifyReleaseCode = rs.string("spotify_release_code"),
      spotifyPlaylistCode = rs.string("spotify_playlist_code")
    )
}
