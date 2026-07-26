package io.github.stoneream.dachshund.infra.db.reader.user_playlist_setting

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.generated.{UserPlaylistSettingDbRow, UserPlaylistSettingTable}
import io.github.stoneream.dachshund.model.PlaylistUsageType
import scalikejdbc.*

@Singleton
class UserPlaylistSettingReader @Inject() () {
  def find(
      userId: Long,
      playlistUsageType: PlaylistUsageType
  )(using DBSession): Option[UserPlaylistSettingDbRow] =
    sql"""
      select
        id,
        user_id,
        playlist_usage_type,
        spotify_playlist_code,
        spotify_playlist_uri,
        playlist_name,
        enabled,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      from
        user_playlist_setting
      where
        user_id = {userId}
        and playlist_usage_type = {playlistUsageType}
        and deleted = 0
      limit 1
    """
      .bindByName(
        "userId" -> userId,
        "playlistUsageType" -> playlistUsageType.dbValue
      )
      .map(UserPlaylistSettingTable.map)
      .single
      .apply()

  def findEnabled(
      userId: Long,
      playlistUsageType: PlaylistUsageType
  )(using DBSession): Option[UserPlaylistSettingDbRow] =
    sql"""
      select
        id,
        user_id,
        playlist_usage_type,
        spotify_playlist_code,
        spotify_playlist_uri,
        playlist_name,
        enabled,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      from
        user_playlist_setting
      where
        user_id = {userId}
        and playlist_usage_type = {playlistUsageType}
        and enabled = 1
        and deleted = 0
      limit 1
    """
      .bindByName(
        "userId" -> userId,
        "playlistUsageType" -> playlistUsageType.dbValue
      )
      .map(UserPlaylistSettingTable.map)
      .single
      .apply()
}
