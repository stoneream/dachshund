package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.generated.UserSessionTokenDbRow
import scalikejdbc.*

@Singleton
class UserSessionTokenWriter @Inject() () {
  def write(row: UserSessionTokenDbRow)(using DBSession): Unit = {
    sql"""
      insert into user_session_token (
        user_id,
        hashed_token,
        issued_at,
        last_accessed_at,
        idle_expires_at,
        expires_at,
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
        {hashedToken},
        {issuedAt},
        {lastAccessedAt},
        {idleExpiresAt},
        {expiresAt},
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
        "hashedToken" -> row.hashedToken,
        "issuedAt" -> row.issuedAt,
        "lastAccessedAt" -> row.lastAccessedAt,
        "idleExpiresAt" -> row.idleExpiresAt,
        "expiresAt" -> row.expiresAt,
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
  }
}
