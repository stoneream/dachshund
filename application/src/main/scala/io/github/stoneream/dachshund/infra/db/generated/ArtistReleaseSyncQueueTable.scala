package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object ArtistReleaseSyncQueueTable {
  val Name = "artist_release_sync_queue"

  object Columns {
    val Id = "id"
    val SpotifyArtistCode = "spotify_artist_code"
    val SyncScope = "sync_scope"
    val Status = "status"
    val IncludeGroups = "include_groups"
    val Market = "market"
    val RequestedLimit = "requested_limit"
    val NextOffset = "next_offset"
    val NextAttemptAt = "next_attempt_at"
    val LastAttemptedAt = "last_attempted_at"
    val CompletedAt = "completed_at"
    val AttemptCount = "attempt_count"
    val LastFailedAt = "last_failed_at"
    val LastErrorType = "last_error_type"
    val LockToken = "lock_token"
    val LockedUntil = "locked_until"
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
      SpotifyArtistCode,
      SyncScope,
      Status,
      IncludeGroups,
      Market,
      RequestedLimit,
      NextOffset,
      NextAttemptAt,
      LastAttemptedAt,
      CompletedAt,
      AttemptCount,
      LastFailedAt,
      LastErrorType,
      LockToken,
      LockedUntil,
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

  def map(rs: WrappedResultSet): ArtistReleaseSyncQueueDbRow =
    ArtistReleaseSyncQueueDbRow(
      id = rs.long(Columns.Id),
      spotifyArtistCode = rs.string(Columns.SpotifyArtistCode),
      syncScope = rs.string(Columns.SyncScope),
      status = rs.string(Columns.Status),
      includeGroups = rs.string(Columns.IncludeGroups),
      market = rs.stringOpt(Columns.Market),
      requestedLimit = rs.int(Columns.RequestedLimit),
      nextOffset = rs.int(Columns.NextOffset),
      nextAttemptAt = rs.localDateTimeOpt(Columns.NextAttemptAt),
      lastAttemptedAt = rs.localDateTimeOpt(Columns.LastAttemptedAt),
      completedAt = rs.localDateTimeOpt(Columns.CompletedAt),
      attemptCount = rs.int(Columns.AttemptCount),
      lastFailedAt = rs.localDateTimeOpt(Columns.LastFailedAt),
      lastErrorType = rs.string(Columns.LastErrorType),
      lockToken = rs.string(Columns.LockToken),
      lockedUntil = rs.localDateTimeOpt(Columns.LockedUntil),
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
