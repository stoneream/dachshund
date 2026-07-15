package io.github.stoneream.dachshund.infra.db.writer

import io.github.stoneream.dachshund.infra.db.generated.UserSpotifyAuthDbRow
import scalikejdbc.*

import javax.inject.{Inject, Singleton}

@Singleton
class SpotifyUserAuthWriter @Inject() () {
  def write(row: UserSpotifyAuthDbRow)(using DBSession): Long = {
    sql"""
      insert into user_spotify_auth (
        user_id,
        spotify_user_id,
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
        {spotifyUserId},
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
      .bindByName(
        "userId" -> row.userId,
        "spotifyUserId" -> row.spotifyUserId,
        "createdAt" -> row.createdAt,
        "updatedAt" -> row.updatedAt,
        "deletedAt" -> row.deletedAt,
        "createdUser" -> row.createdUser,
        "updatedUser" -> row.updatedUser,
        "deletedUser" -> row.deletedUser,
        "deleted" -> row.deleted,
        "lockVersion" -> row.lockVersion
      )
      .updateAndReturnGeneratedKey
      .apply()
  }
}
