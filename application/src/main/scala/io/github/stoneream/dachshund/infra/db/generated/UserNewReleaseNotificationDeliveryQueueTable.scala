package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object UserNewReleaseNotificationDeliveryQueueTable {
  val Name = "user_new_release_notification_delivery_queue"

  object Columns {
    val Id = "id"
    val UserNewReleaseEventId = "user_new_release_event_id"
    val ReleaseNotificationType = "release_notification_type"
    val PlaylistSettingId = "playlist_setting_id"
    val Status = "status"
    val NextAttemptAt = "next_attempt_at"
    val AttemptCount = "attempt_count"
    val LastFailedAt = "last_failed_at"
    val LastErrorType = "last_error_type"
    val LockToken = "lock_token"
    val LockedUntil = "locked_until"
    val LastAttemptedAt = "last_attempted_at"
    val CompletedAt = "completed_at"
    val SpotifySnapshotId = "spotify_snapshot_id"
    val CreatedAt = "created_at"
    val UpdatedAt = "updated_at"
    val DeletedAt = "deleted_at"
    val CreatedUser = "created_user"
    val UpdatedUser = "updated_user"
    val DeletedUser = "deleted_user"
    val Deleted = "deleted"
    val LockVersion = "lock_version"

    val All: Seq[String] = Seq(
      Id,
      UserNewReleaseEventId,
      ReleaseNotificationType,
      PlaylistSettingId,
      Status,
      NextAttemptAt,
      AttemptCount,
      LastFailedAt,
      LastErrorType,
      LockToken,
      LockedUntil,
      LastAttemptedAt,
      CompletedAt,
      SpotifySnapshotId,
      CreatedAt,
      UpdatedAt,
      DeletedAt,
      CreatedUser,
      UpdatedUser,
      DeletedUser,
      Deleted,
      LockVersion
    )
  }

  val InsertAuditColumnNames: Seq[String] = Seq(Columns.CreatedAt, Columns.CreatedUser)
  val UpdateAuditColumnNames: Seq[String] = Seq(Columns.UpdatedAt, Columns.UpdatedUser, Columns.LockVersion)
  val DeleteAuditColumnNames: Seq[String] = Seq(Columns.DeletedAt, Columns.DeletedUser, Columns.Deleted)

  def map(rs: WrappedResultSet): UserNewReleaseNotificationDeliveryQueueDbRow =
    UserNewReleaseNotificationDeliveryQueueDbRow(
      id = rs.long(Columns.Id),
      userNewReleaseEventId = rs.long(Columns.UserNewReleaseEventId),
      releaseNotificationType = rs.string(Columns.ReleaseNotificationType),
      playlistSettingId = rs.long(Columns.PlaylistSettingId),
      status = rs.string(Columns.Status),
      nextAttemptAt = rs.localDateTimeOpt(Columns.NextAttemptAt),
      attemptCount = rs.int(Columns.AttemptCount),
      lastFailedAt = rs.localDateTimeOpt(Columns.LastFailedAt),
      lastErrorType = rs.string(Columns.LastErrorType),
      lockToken = rs.string(Columns.LockToken),
      lockedUntil = rs.localDateTimeOpt(Columns.LockedUntil),
      lastAttemptedAt = rs.localDateTimeOpt(Columns.LastAttemptedAt),
      completedAt = rs.localDateTimeOpt(Columns.CompletedAt),
      spotifySnapshotId = rs.string(Columns.SpotifySnapshotId),
      createdAt = rs.localDateTime(Columns.CreatedAt),
      updatedAt = rs.localDateTime(Columns.UpdatedAt),
      deletedAt = rs.localDateTimeOpt(Columns.DeletedAt),
      createdUser = rs.string(Columns.CreatedUser),
      updatedUser = rs.string(Columns.UpdatedUser),
      deletedUser = rs.string(Columns.DeletedUser),
      deleted = rs.long(Columns.Deleted),
      lockVersion = rs.long(Columns.LockVersion)
    )
}
