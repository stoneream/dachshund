package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object ReleaseTrackTable {
  val Name = "release_track"

  object Columns {
    val Id = "id"
    val ArtistReleaseId = "artist_release_id"
    val SpotifyTrackCode = "spotify_track_code"
    val TrackName = "track_name"
    val SpotifyTrackUri = "spotify_track_uri"
    val SpotifyUrl = "spotify_url"
    val Href = "href"
    val DiscNumber = "disc_number"
    val TrackNumber = "track_number"
    val DurationMs = "duration_ms"
    val Explicit = "explicit"
    val IsPlayable = "is_playable"
    val IsLocal = "is_local"
    val LinkedFromSpotifyTrackCode = "linked_from_spotify_track_code"
    val LinkedFromSpotifyTrackUri = "linked_from_spotify_track_uri"
    val PreviewUrl = "preview_url"
    val ExternalIdsJson = "external_ids_json"
    val IsrcCode = "isrc_code"
    val EanCode = "ean_code"
    val UpcCode = "upc_code"
    val AvailableMarketsJson = "available_markets_json"
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
      ArtistReleaseId,
      SpotifyTrackCode,
      TrackName,
      SpotifyTrackUri,
      SpotifyUrl,
      Href,
      DiscNumber,
      TrackNumber,
      DurationMs,
      Explicit,
      IsPlayable,
      IsLocal,
      LinkedFromSpotifyTrackCode,
      LinkedFromSpotifyTrackUri,
      PreviewUrl,
      ExternalIdsJson,
      IsrcCode,
      EanCode,
      UpcCode,
      AvailableMarketsJson,
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

  def map(rs: WrappedResultSet): ReleaseTrackDbRow =
    ReleaseTrackDbRow(
      id = rs.long(Columns.Id),
      artistReleaseId = rs.long(Columns.ArtistReleaseId),
      spotifyTrackCode = rs.string(Columns.SpotifyTrackCode),
      trackName = rs.string(Columns.TrackName),
      spotifyTrackUri = rs.string(Columns.SpotifyTrackUri),
      spotifyUrl = rs.string(Columns.SpotifyUrl),
      href = rs.string(Columns.Href),
      discNumber = rs.int(Columns.DiscNumber),
      trackNumber = rs.int(Columns.TrackNumber),
      durationMs = rs.intOpt(Columns.DurationMs),
      explicit = rs.longOpt(Columns.Explicit),
      isPlayable = rs.longOpt(Columns.IsPlayable),
      isLocal = rs.longOpt(Columns.IsLocal),
      linkedFromSpotifyTrackCode = rs.stringOpt(Columns.LinkedFromSpotifyTrackCode),
      linkedFromSpotifyTrackUri = rs.stringOpt(Columns.LinkedFromSpotifyTrackUri),
      previewUrl = rs.stringOpt(Columns.PreviewUrl),
      externalIdsJson = rs.stringOpt(Columns.ExternalIdsJson),
      isrcCode = rs.stringOpt(Columns.IsrcCode),
      eanCode = rs.stringOpt(Columns.EanCode),
      upcCode = rs.stringOpt(Columns.UpcCode),
      availableMarketsJson = rs.stringOpt(Columns.AvailableMarketsJson),
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
