package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object UserFollowedArtistTable {
  val Name = "user_followed_artist"

  object Columns {
    val Id = "id"
    val UserId = "user_id"
    val SpotifyArtistCode = "spotify_artist_code"
    val ArtistName = "artist_name"
    val SpotifyArtistUri = "spotify_artist_uri"
    val SpotifyUrl = "spotify_url"
    val Href = "href"
    val PrimaryImageUrl = "primary_image_url"
    val PrimaryImageHeight = "primary_image_height"
    val PrimaryImageWidth = "primary_image_width"
    val ImagesJson = "images_json"
    val GenresJson = "genres_json"
    val FollowersTotal = "followers_total"
    val Popularity = "popularity"
    val FirstFollowedAt = "first_followed_at"
    val LastSeenAt = "last_seen_at"
    val LastSyncedAt = "last_synced_at"
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
      SpotifyArtistCode,
      ArtistName,
      SpotifyArtistUri,
      SpotifyUrl,
      Href,
      PrimaryImageUrl,
      PrimaryImageHeight,
      PrimaryImageWidth,
      ImagesJson,
      GenresJson,
      FollowersTotal,
      Popularity,
      FirstFollowedAt,
      LastSeenAt,
      LastSyncedAt,
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

  def map(rs: WrappedResultSet): UserFollowedArtistDbRow =
    UserFollowedArtistDbRow(
      id = rs.long(Columns.Id),
      userId = rs.long(Columns.UserId),
      spotifyArtistCode = rs.string(Columns.SpotifyArtistCode),
      artistName = rs.string(Columns.ArtistName),
      spotifyArtistUri = rs.string(Columns.SpotifyArtistUri),
      spotifyUrl = rs.string(Columns.SpotifyUrl),
      href = rs.string(Columns.Href),
      primaryImageUrl = rs.string(Columns.PrimaryImageUrl),
      primaryImageHeight = rs.intOpt(Columns.PrimaryImageHeight),
      primaryImageWidth = rs.intOpt(Columns.PrimaryImageWidth),
      imagesJson = rs.stringOpt(Columns.ImagesJson),
      genresJson = rs.stringOpt(Columns.GenresJson),
      followersTotal = rs.longOpt(Columns.FollowersTotal),
      popularity = rs.intOpt(Columns.Popularity),
      firstFollowedAt = rs.localDateTimeOpt(Columns.FirstFollowedAt),
      lastSeenAt = rs.localDateTimeOpt(Columns.LastSeenAt),
      lastSyncedAt = rs.localDateTimeOpt(Columns.LastSyncedAt),
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
