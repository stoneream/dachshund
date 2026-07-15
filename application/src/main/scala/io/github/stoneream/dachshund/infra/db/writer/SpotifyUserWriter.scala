package io.github.stoneream.dachshund.infra.db.writer

import io.github.stoneream.dachshund.infra.db.generated.UserDbRow
import scalikejdbc.*

import com.google.inject.{Inject, Singleton}

@Singleton
class SpotifyUserWriter @Inject() () {
  def write(row: UserDbRow)(using DBSession): Long = {
    sql"""
      insert into user (
        user_name,
        display_name,
        time_zone,
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
        {userName},
        {displayName},
        {timeZone},
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
      .bindByName(
        "userName" -> row.userName,
        "displayName" -> row.displayName,
        "timeZone" -> row.timeZone,
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
      .updateAndReturnGeneratedKey
      .apply()
  }
}
