package io.github.stoneream.dachshund.infra.db.reader.followed_artists_sync

import com.google.inject.{Inject, Singleton}
import scalikejdbc.*

import java.time.LocalDateTime

object UserFollowedArtistsReader {
  final case class DeletionTarget(
      id: Long,
      userId: Long,
      lockVersion: Long
  )
}

@Singleton
class UserFollowedArtistsReader @Inject() () {
  import UserFollowedArtistsReader.DeletionTarget

  def findDeletionTargetsForUpdate(
      userId: Long,
      lastSeenAt: LocalDateTime
  )(using DBSession): Seq[DeletionTarget] =
    sql"""
      select
        id,
        user_id,
        lock_version
      from
        user_followed_artist
      where
        user_id = {userId}
        and deleted = 0
        and (
          last_seen_at is null
          or last_seen_at <> {lastSeenAt}
        )
      order by
        id asc
      for update
    """
      .bindByName(
        "userId" -> userId,
        "lastSeenAt" -> lastSeenAt
      )
      .map { rs =>
        DeletionTarget(
          id = rs.long("id"),
          userId = rs.long("user_id"),
          lockVersion = rs.long("lock_version")
        )
      }
      .list
      .apply()
}
