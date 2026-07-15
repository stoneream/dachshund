package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserFollowedArtistDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import scalikejdbc.*

@Singleton
class UserFollowedArtistsWriter @Inject() () {
  def write(row: UserFollowedArtistDbRow)(using DBSession): Int =
    sql"""
      insert into user_followed_artist (
        user_id,
        spotify_artist_code,
        artist_name,
        spotify_artist_uri,
        spotify_url,
        href,
        primary_image_url,
        primary_image_height,
        primary_image_width,
        images_json,
        genres_json,
        followers_total,
        popularity,
        first_followed_at,
        last_seen_at,
        last_synced_at,
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
        {spotifyArtistCode},
        {artistName},
        {spotifyArtistUri},
        {spotifyUrl},
        {href},
        {primaryImageUrl},
        {primaryImageHeight},
        {primaryImageWidth},
        {imagesJson},
        {genresJson},
        {followersTotal},
        {popularity},
        {firstFollowedAt},
        {lastSeenAt},
        {lastSyncedAt},
        {createdAt},
        {updatedAt},
        {deletedAt},
        {createdUser},
        {updatedUser},
        {deletedUser},
        {deleted},
        {lockVersion}
      )
      on duplicate key update
        artist_name = {artistName},
        spotify_artist_uri = {spotifyArtistUri},
        spotify_url = {spotifyUrl},
        href = {href},
        primary_image_url = {primaryImageUrl},
        primary_image_height = {primaryImageHeight},
        primary_image_width = {primaryImageWidth},
        images_json = {imagesJson},
        genres_json = {genresJson},
        followers_total = {followersTotal},
        popularity = {popularity},
        first_followed_at = coalesce(first_followed_at, {firstFollowedAt}),
        last_seen_at = {lastSeenAt},
        last_synced_at = {lastSyncedAt},
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        deleted_at = {deletedAt},
        deleted_user = {deletedUser},
        deleted = {deleted},
        lock_version = lock_version + 1
    """
      .bindByName(
        "userId" -> row.userId,
        "spotifyArtistCode" -> row.spotifyArtistCode,
        "artistName" -> row.artistName,
        "spotifyArtistUri" -> row.spotifyArtistUri,
        "spotifyUrl" -> row.spotifyUrl,
        "href" -> row.href,
        "primaryImageUrl" -> row.primaryImageUrl,
        "primaryImageHeight" -> row.primaryImageHeight,
        "primaryImageWidth" -> row.primaryImageWidth,
        "imagesJson" -> row.imagesJson,
        "genresJson" -> row.genresJson,
        "followersTotal" -> row.followersTotal,
        "popularity" -> row.popularity,
        "firstFollowedAt" -> row.firstFollowedAt,
        "lastSeenAt" -> row.lastSeenAt,
        "lastSyncedAt" -> row.lastSyncedAt,
        "createdAt" -> row.createdAt,
        "updatedAt" -> row.updatedAt,
        "deletedAt" -> row.deletedAt,
        "createdUser" -> row.createdUser,
        "updatedUser" -> row.updatedUser,
        "deletedUser" -> row.deletedUser,
        "deleted" -> row.deleted,
        "lockVersion" -> row.lockVersion
      )
      .update
      .apply()

  def update(
      id: Long,
      userId: Long,
      expectedLockVersion: Long,
      deleted: Long,
      deletedAt: BusinessDateTime,
      deletedUser: AuditUser,
      lastSyncedAt: BusinessDateTime,
      updatedAt: BusinessDateTime,
      updatedUser: AuditUser,
      lockVersion: Long
  )(using DBSession): Boolean =
    sql"""
      update
        user_followed_artist
      set
        deleted = {deleted},
        deleted_at = {deletedAt},
        deleted_user = {deletedUser},
        last_synced_at = {lastSyncedAt},
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        lock_version = {lockVersion}
      where
        id = {id}
        and user_id = {userId}
        and lock_version = {expectedLockVersion}
        and deleted = 0
    """
      .bindByName(
        "id" -> id,
        "userId" -> userId,
        "expectedLockVersion" -> expectedLockVersion,
        "deleted" -> deleted,
        "deletedAt" -> deletedAt.toLocalDateTime,
        "deletedUser" -> deletedUser.dbValue,
        "lastSyncedAt" -> lastSyncedAt.toLocalDateTime,
        "updatedAt" -> updatedAt.toLocalDateTime,
        "updatedUser" -> updatedUser.dbValue,
        "lockVersion" -> lockVersion
      )
      .update
      .apply() == 1
}
