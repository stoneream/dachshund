package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object ArtistReleaseTable {
  val Name = "artist_release"

  object Columns {
    val Id = "id"
    val SpotifyReleaseCode = "spotify_release_code"
    val SourceSpotifyArtistCode = "source_spotify_artist_code"
    val ReleaseName = "release_name"
    val ReleaseType = "release_type"
    val AlbumType = "album_type"
    val AlbumGroup = "album_group"
    val SpotifyReleaseUri = "spotify_release_uri"
    val SpotifyUrl = "spotify_url"
    val Href = "href"
    val PrimaryImageUrl = "primary_image_url"
    val PrimaryImageHeight = "primary_image_height"
    val PrimaryImageWidth = "primary_image_width"
    val ImagesJson = "images_json"
    val ReleaseDateText = "release_date_text"
    val ReleaseDatePrecision = "release_date_precision"
    val ReleaseDateAt = "release_date_at"
    val TotalTracksCount = "total_tracks_count"
    val LabelName = "label_name"
    val NormalizedLabelName = "normalized_label_name"
    val ExternalIdsJson = "external_ids_json"
    val UpcCode = "upc_code"
    val EanCode = "ean_code"
    val IsrcCode = "isrc_code"
    val CopyrightsJson = "copyrights_json"
    val AvailableMarketsJson = "available_markets_json"
    val GenresJson = "genres_json"
    val RestrictionsJson = "restrictions_json"
    val Popularity = "popularity"
    val SyncedAt = "synced_at"
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
      SpotifyReleaseCode,
      SourceSpotifyArtistCode,
      ReleaseName,
      ReleaseType,
      AlbumType,
      AlbumGroup,
      SpotifyReleaseUri,
      SpotifyUrl,
      Href,
      PrimaryImageUrl,
      PrimaryImageHeight,
      PrimaryImageWidth,
      ImagesJson,
      ReleaseDateText,
      ReleaseDatePrecision,
      ReleaseDateAt,
      TotalTracksCount,
      LabelName,
      NormalizedLabelName,
      ExternalIdsJson,
      UpcCode,
      EanCode,
      IsrcCode,
      CopyrightsJson,
      AvailableMarketsJson,
      GenresJson,
      RestrictionsJson,
      Popularity,
      SyncedAt,
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

  def map(rs: WrappedResultSet): ArtistReleaseDbRow =
    ArtistReleaseDbRow(
      id = rs.long(Columns.Id),
      spotifyReleaseCode = rs.string(Columns.SpotifyReleaseCode),
      sourceSpotifyArtistCode = rs.string(Columns.SourceSpotifyArtistCode),
      releaseName = rs.string(Columns.ReleaseName),
      releaseType = rs.string(Columns.ReleaseType),
      albumType = rs.string(Columns.AlbumType),
      albumGroup = rs.stringOpt(Columns.AlbumGroup),
      spotifyReleaseUri = rs.string(Columns.SpotifyReleaseUri),
      spotifyUrl = rs.string(Columns.SpotifyUrl),
      href = rs.string(Columns.Href),
      primaryImageUrl = rs.string(Columns.PrimaryImageUrl),
      primaryImageHeight = rs.intOpt(Columns.PrimaryImageHeight),
      primaryImageWidth = rs.intOpt(Columns.PrimaryImageWidth),
      imagesJson = rs.stringOpt(Columns.ImagesJson),
      releaseDateText = rs.string(Columns.ReleaseDateText),
      releaseDatePrecision = rs.string(Columns.ReleaseDatePrecision),
      releaseDateAt = rs.localDateTimeOpt(Columns.ReleaseDateAt),
      totalTracksCount = rs.intOpt(Columns.TotalTracksCount),
      labelName = rs.stringOpt(Columns.LabelName),
      normalizedLabelName = rs.stringOpt(Columns.NormalizedLabelName),
      externalIdsJson = rs.stringOpt(Columns.ExternalIdsJson),
      upcCode = rs.stringOpt(Columns.UpcCode),
      eanCode = rs.stringOpt(Columns.EanCode),
      isrcCode = rs.stringOpt(Columns.IsrcCode),
      copyrightsJson = rs.stringOpt(Columns.CopyrightsJson),
      availableMarketsJson = rs.stringOpt(Columns.AvailableMarketsJson),
      genresJson = rs.stringOpt(Columns.GenresJson),
      restrictionsJson = rs.stringOpt(Columns.RestrictionsJson),
      popularity = rs.intOpt(Columns.Popularity),
      syncedAt = rs.localDateTimeOpt(Columns.SyncedAt),
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
