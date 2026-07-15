package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object UserNewReleaseEventTable {
  val Name = "user_new_release_event"

  object Columns {
    val Id = "id"
    val UserId = "user_id"
    val ArtistReleaseId = "artist_release_id"
    val SpotifyReleaseCode = "spotify_release_code"
    val SourceSpotifyArtistCode = "source_spotify_artist_code"
    val DetectedAt = "detected_at"
    val DetectionSyncCode = "detection_sync_code"
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
      UserId,
      ArtistReleaseId,
      SpotifyReleaseCode,
      SourceSpotifyArtistCode,
      DetectedAt,
      DetectionSyncCode,
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

  def map(rs: WrappedResultSet): UserNewReleaseEventDbRow =
    UserNewReleaseEventDbRow(
      id = rs.long(Columns.Id),
      userId = rs.long(Columns.UserId),
      artistReleaseId = rs.long(Columns.ArtistReleaseId),
      spotifyReleaseCode = rs.string(Columns.SpotifyReleaseCode),
      sourceSpotifyArtistCode = rs.string(Columns.SourceSpotifyArtistCode),
      detectedAt = rs.localDateTime(Columns.DetectedAt),
      detectionSyncCode = rs.string(Columns.DetectionSyncCode),
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
