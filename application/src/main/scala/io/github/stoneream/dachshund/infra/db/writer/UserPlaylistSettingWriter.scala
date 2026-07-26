package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.generated.UserPlaylistSettingDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import scalikejdbc.*

@Singleton
class UserPlaylistSettingWriter @Inject() () {
  def write(row: UserPlaylistSettingDbRow)(using DBSession): Long =
    insert(row).updateAndReturnGeneratedKey
      .apply()

  def writeIfAbsent(row: UserPlaylistSettingDbRow)(using DBSession): Int =
    insert(row, ignore = true).update
      .apply()

  def updateEnabled(
      id: Long,
      enabled: Long,
      updatedAt: BusinessDateTime,
      updatedUser: String
  )(using DBSession): Int =
    sql"""
      update
        user_playlist_setting
      set
        enabled = {enabled},
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        lock_version = lock_version + 1
      where
        id = {id}
        and deleted = 0
    """
      .bindByName(
        "id" -> id,
        "enabled" -> enabled,
        "updatedAt" -> updatedAt.toLocalDateTime,
        "updatedUser" -> updatedUser
      )
      .update
      .apply()

  private def insert(row: UserPlaylistSettingDbRow, ignore: Boolean = false): SQL[Nothing, NoExtractor] =
    SQL(
      s"""
      insert ${if (ignore) "ignore " else ""}into user_playlist_setting (
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
      ) values (
        {userId},
        {playlistUsageType},
        {spotifyPlaylistCode},
        {spotifyPlaylistUri},
        {playlistName},
        {enabled},
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
    )
      .bindByName(
        "userId" -> row.userId,
        "playlistUsageType" -> row.playlistUsageType,
        "spotifyPlaylistCode" -> row.spotifyPlaylistCode,
        "spotifyPlaylistUri" -> row.spotifyPlaylistUri,
        "playlistName" -> row.playlistName,
        "enabled" -> row.enabled,
        "createdAt" -> row.createdAt,
        "updatedAt" -> row.updatedAt,
        "deletedAt" -> row.deletedAt,
        "createdUser" -> row.createdUser,
        "updatedUser" -> row.updatedUser,
        "deletedUser" -> row.deletedUser,
        "deleted" -> row.deleted,
        "lockVersion" -> row.lockVersion
      )
}
