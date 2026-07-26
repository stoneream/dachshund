package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object UserPlaylistSettingTable {
  val Name = "user_playlist_setting"

  object Columns {
    val Id = "id"
    val UserId = "user_id"
    val PlaylistUsageType = "playlist_usage_type"
    val SpotifyPlaylistCode = "spotify_playlist_code"
    val SpotifyPlaylistUri = "spotify_playlist_uri"
    val PlaylistName = "playlist_name"
    val Enabled = "enabled"
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
      PlaylistUsageType,
      SpotifyPlaylistCode,
      SpotifyPlaylistUri,
      PlaylistName,
      Enabled,
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

  def map(rs: WrappedResultSet): UserPlaylistSettingDbRow =
    UserPlaylistSettingDbRow(
      id = rs.long(Columns.Id),
      userId = rs.long(Columns.UserId),
      playlistUsageType = rs.string(Columns.PlaylistUsageType),
      spotifyPlaylistCode = rs.string(Columns.SpotifyPlaylistCode),
      spotifyPlaylistUri = rs.string(Columns.SpotifyPlaylistUri),
      playlistName = rs.string(Columns.PlaylistName),
      enabled = rs.long(Columns.Enabled),
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
