package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object UserSpotifyAuthorizationRefreshQueueTable {
  val Name = "user_spotify_authorization_refresh_queue"

  object Columns {
    val Id = "id"
    val AuthorizationId = "authorization_id"
    val Status = "status"
    val NextAttemptAt = "next_attempt_at"
    val AttemptCount = "attempt_count"
    val LastFailedAt = "last_failed_at"
    val LastErrorType = "last_error_type"
    val LockToken = "lock_token"
    val LockedUntil = "locked_until"
    val LastAttemptedAt = "last_attempted_at"
    val CompletedAt = "completed_at"
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
      AuthorizationId,
      Status,
      NextAttemptAt,
      AttemptCount,
      LastFailedAt,
      LastErrorType,
      LockToken,
      LockedUntil,
      LastAttemptedAt,
      CompletedAt,
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

  def map(rs: WrappedResultSet): UserSpotifyAuthorizationRefreshQueueDbRow =
    UserSpotifyAuthorizationRefreshQueueDbRow(
      id = rs.long(Columns.Id),
      authorizationId = rs.long(Columns.AuthorizationId),
      status = rs.string(Columns.Status),
      nextAttemptAt = rs.localDateTimeOpt(Columns.NextAttemptAt),
      attemptCount = rs.int(Columns.AttemptCount),
      lastFailedAt = rs.localDateTimeOpt(Columns.LastFailedAt),
      lastErrorType = rs.string(Columns.LastErrorType),
      lockToken = rs.string(Columns.LockToken),
      lockedUntil = rs.localDateTimeOpt(Columns.LockedUntil),
      lastAttemptedAt = rs.localDateTimeOpt(Columns.LastAttemptedAt),
      completedAt = rs.localDateTimeOpt(Columns.CompletedAt),
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
