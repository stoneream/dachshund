package io.github.stoneream.dachshund.infra.db.reader.auth.callback

import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

import com.google.inject.{Inject, Singleton}

object SpotifyAuthorizationReader {
  final case class AuthorizationRow(
      authorizationId: Long,
      userId: Long,
      lockVersion: Long,
      deleted: Long
  )

  final case class RefreshQueueRow(
      queueId: Long,
      authorizationId: Long,
      status: QueueJobStatus,
      lockToken: String,
      lockVersion: Long,
      deleted: Long
  )
}

@Singleton
class SpotifyAuthorizationReader @Inject() () {
  import SpotifyAuthorizationReader.{AuthorizationRow, RefreshQueueRow}

  def findUserIdBySpotifyUserId(spotifyUserId: String)(using DBSession): Option[Long] =
    sql"""
      select
        usa.user_id
      from
        user_spotify_auth usa
        inner join user u on u.id = usa.user_id
      where
        usa.spotify_user_id = {spotifyUserId}
        and usa.deleted = 0
        and u.deleted = 0
      limit 1
    """
      .bindByName("spotifyUserId" -> spotifyUserId)
      .map(_.long("user_id"))
      .single
      .apply()

  def findAuthorizationByUserId(userId: Long)(using DBSession): Option[AuthorizationRow] =
    sql"""
      select
        usa.id,
        usa.user_id,
        usa.lock_version,
        usa.deleted
      from
        user_spotify_authorization usa
      where
        usa.user_id = {userId}
      limit 1
    """
      .bindByName("userId" -> userId)
      .map { row =>
        AuthorizationRow(
          authorizationId = row.long("id"),
          userId = row.long("user_id"),
          lockVersion = row.long("lock_version"),
          deleted = row.long("deleted")
        )
      }
      .single
      .apply()

  def findAuthorizationIdByUserId(userId: Long)(using DBSession): Option[Long] =
    sql"""
      select
        usa.id
      from
        user_spotify_authorization usa
      where
        usa.user_id = {userId}
        and usa.deleted = 0
      limit 1
    """
      .bindByName("userId" -> userId)
      .map(_.long("id"))
      .single
      .apply()

  def findRefreshQueueByAuthorizationId(authorizationId: Long)(using DBSession): Option[RefreshQueueRow] =
    sql"""
      select
        usarq.id,
        usarq.authorization_id,
        usarq.status,
        usarq.lock_token,
        usarq.lock_version,
        usarq.deleted
      from
        user_spotify_authorization_refresh_queue usarq
      where
        usarq.authorization_id = {authorizationId}
      limit 1
    """
      .bindByName("authorizationId" -> authorizationId)
      .map { row =>
        RefreshQueueRow(
          queueId = row.long("id"),
          authorizationId = row.long("authorization_id"),
          status = QueueJobStatus.fromDbValue(row.string("status")),
          lockToken = row.string("lock_token"),
          lockVersion = row.long("lock_version"),
          deleted = row.long("deleted")
        )
      }
      .single
      .apply()
}
