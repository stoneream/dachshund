package io.github.stoneream.dachshund.infra.db.reader.auth

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.auth.UserSessionContext.NormalUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import scalikejdbc.*

@Singleton
class UserSessionTokenReader @Inject() () {
  def findUserByHashedToken(
      hashedToken: String,
      now: BusinessDateTime
  )(using DBSession): Option[NormalUser] =
    sql"""
      select
        u.id,
        u.user_name,
        u.display_name
      from
        user_session_token ust
        inner join user u on u.id = ust.user_id
      where
        ust.hashed_token = {hashedToken}
        and ust.expires_at >= {now}
        and ust.idle_expires_at >= {now}
        and ust.deleted = 0
        and u.enabled = 1
        and u.deleted = 0
      limit 1
    """
      .bindByName(
        "hashedToken" -> hashedToken,
        "now" -> now.toLocalDateTime
      )
      .map { rs =>
        NormalUser(
          userId = rs.long("id"),
          userName = rs.string("user_name"),
          displayName = rs.string("display_name")
        )
      }
      .single
      .apply()
}
