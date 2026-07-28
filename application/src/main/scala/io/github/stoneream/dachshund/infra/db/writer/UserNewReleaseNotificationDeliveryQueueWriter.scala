package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserNewReleaseNotificationDeliveryQueueDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.{QueueJobStatus, ReleaseNotificationType}
import scalikejdbc.*

@Singleton
class UserNewReleaseNotificationDeliveryQueueWriter @Inject() () {
  def write(row: UserNewReleaseNotificationDeliveryQueueDbRow)(using DBSession): Int =
    sql"""
      insert ignore into user_new_release_notification_delivery_queue (
        user_new_release_event_id,
        release_notification_type,
        playlist_setting_id,
        status,
        next_attempt_at,
        attempt_count,
        last_failed_at,
        last_error_type,
        lock_token,
        locked_until,
        last_attempted_at,
        completed_at,
        spotify_snapshot_id,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {userNewReleaseEventId},
        {releaseNotificationType},
        {playlistSettingId},
        {status},
        {nextAttemptAt},
        {attemptCount},
        {lastFailedAt},
        {lastErrorType},
        {lockToken},
        {lockedUntil},
        {lastAttemptedAt},
        {completedAt},
        {spotifySnapshotId},
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
        "userNewReleaseEventId" -> row.userNewReleaseEventId,
        "releaseNotificationType" -> row.releaseNotificationType,
        "playlistSettingId" -> row.playlistSettingId,
        "status" -> row.status,
        "nextAttemptAt" -> row.nextAttemptAt,
        "attemptCount" -> row.attemptCount,
        "lastFailedAt" -> row.lastFailedAt,
        "lastErrorType" -> row.lastErrorType,
        "lockToken" -> row.lockToken,
        "lockedUntil" -> row.lockedUntil,
        "lastAttemptedAt" -> row.lastAttemptedAt,
        "completedAt" -> row.completedAt,
        "spotifySnapshotId" -> row.spotifySnapshotId,
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
      userNewReleaseEventId: Long,
      releaseNotificationType: ReleaseNotificationType,
      playlistSettingId: Long,
      expectedStatus: QueueJobStatus,
      expectedLockToken: String,
      expectedQueueLockVersion: Long,
      expectedDeleted: Long,
      status: QueueJobStatus,
      nextAttemptAt: Option[BusinessDateTime],
      attemptCount: Int,
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      spotifySnapshotId: String,
      updatedAt: BusinessDateTime,
      deletedAt: Option[BusinessDateTime],
      updatedUser: AuditUser,
      deletedUser: AuditUser,
      deleted: Long,
      lockVersion: Long
  )(using DBSession): Boolean =
    sql"""
      update
        user_new_release_notification_delivery_queue
      set
        status = {status},
        next_attempt_at = {nextAttemptAt},
        attempt_count = {attemptCount},
        last_failed_at = {lastFailedAt},
        last_error_type = {lastErrorType},
        lock_token = {lockToken},
        locked_until = {lockedUntil},
        last_attempted_at = {lastAttemptedAt},
        completed_at = {completedAt},
        spotify_snapshot_id = {spotifySnapshotId},
        updated_at = {updatedAt},
        deleted_at = {deletedAt},
        updated_user = {updatedUser},
        deleted_user = {deletedUser},
        deleted = {deleted},
        lock_version = {lockVersion}
      where
        id = {queueId}
        and user_new_release_event_id = {userNewReleaseEventId}
        and release_notification_type = {releaseNotificationType}
        and playlist_setting_id = {playlistSettingId}
        and status = {expectedStatus}
        and lock_token = {expectedLockToken}
        and lock_version = {expectedQueueLockVersion}
        and deleted = {expectedDeleted}
    """
      .bindByName(
        "queueId" -> queueId,
        "userNewReleaseEventId" -> userNewReleaseEventId,
        "releaseNotificationType" -> releaseNotificationType.dbValue,
        "playlistSettingId" -> playlistSettingId,
        "expectedStatus" -> expectedStatus.dbValue,
        "expectedLockToken" -> expectedLockToken,
        "expectedQueueLockVersion" -> expectedQueueLockVersion,
        "expectedDeleted" -> expectedDeleted,
        "status" -> status.dbValue,
        "nextAttemptAt" -> nextAttemptAt.map(_.toLocalDateTime),
        "attemptCount" -> attemptCount,
        "lastFailedAt" -> lastFailedAt.map(_.toLocalDateTime),
        "lastErrorType" -> lastErrorType,
        "lockToken" -> lockToken,
        "lockedUntil" -> lockedUntil.map(_.toLocalDateTime),
        "lastAttemptedAt" -> lastAttemptedAt.map(_.toLocalDateTime),
        "completedAt" -> completedAt.map(_.toLocalDateTime),
        "spotifySnapshotId" -> spotifySnapshotId,
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
