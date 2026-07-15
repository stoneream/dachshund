package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.ArtistReleaseSyncQueueDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

@Singleton
class ArtistReleaseSyncQueueWriter @Inject() () {
  def write(row: ArtistReleaseSyncQueueDbRow)(using DBSession): Int =
    sql"""
      insert into artist_release_sync_queue (
        spotify_artist_code,
        sync_scope,
        status,
        include_groups,
        market,
        requested_limit,
        next_offset,
        next_attempt_at,
        last_attempted_at,
        completed_at,
        attempt_count,
        last_failed_at,
        last_error_type,
        lock_token,
        locked_until,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {spotifyArtistCode},
        {syncScope},
        {status},
        {includeGroups},
        {market},
        {requestedLimit},
        {nextOffset},
        {nextAttemptAt},
        {lastAttemptedAt},
        {completedAt},
        {attemptCount},
        {lastFailedAt},
        {lastErrorType},
        {lockToken},
        {lockedUntil},
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
        "spotifyArtistCode" -> row.spotifyArtistCode,
        "syncScope" -> row.syncScope,
        "status" -> row.status,
        "includeGroups" -> row.includeGroups,
        "market" -> row.market,
        "requestedLimit" -> row.requestedLimit,
        "nextOffset" -> row.nextOffset,
        "nextAttemptAt" -> row.nextAttemptAt,
        "lastAttemptedAt" -> row.lastAttemptedAt,
        "completedAt" -> row.completedAt,
        "attemptCount" -> row.attemptCount,
        "lastFailedAt" -> row.lastFailedAt,
        "lastErrorType" -> row.lastErrorType,
        "lockToken" -> row.lockToken,
        "lockedUntil" -> row.lockedUntil,
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
      queueId: Long,
      spotifyArtistCode: String,
      syncScope: String,
      expectedStatus: QueueJobStatus,
      expectedLockToken: String,
      expectedQueueLockVersion: Long,
      expectedDeleted: Long,
      status: QueueJobStatus,
      includeGroups: String,
      market: Option[String],
      requestedLimit: Int,
      nextOffset: Int,
      nextAttemptAt: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      attemptCount: Int,
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      updatedAt: BusinessDateTime,
      deletedAt: Option[BusinessDateTime],
      updatedUser: AuditUser,
      deletedUser: AuditUser,
      deleted: Long,
      lockVersion: Long
  )(using DBSession): Boolean =
    sql"""
      update
        artist_release_sync_queue
      set
        status = {status},
        include_groups = {includeGroups},
        market = {market},
        requested_limit = {requestedLimit},
        next_offset = {nextOffset},
        next_attempt_at = {nextAttemptAt},
        last_attempted_at = {lastAttemptedAt},
        completed_at = {completedAt},
        attempt_count = {attemptCount},
        last_failed_at = {lastFailedAt},
        last_error_type = {lastErrorType},
        lock_token = {lockToken},
        locked_until = {lockedUntil},
        updated_at = {updatedAt},
        deleted_at = {deletedAt},
        updated_user = {updatedUser},
        deleted_user = {deletedUser},
        deleted = {deleted},
        lock_version = {lockVersion}
      where
        id = {queueId}
        and spotify_artist_code = {spotifyArtistCode}
        and sync_scope = {syncScope}
        and status = {expectedStatus}
        and lock_token = {expectedLockToken}
        and lock_version = {expectedQueueLockVersion}
        and deleted = {expectedDeleted}
    """
      .bindByName(
        "queueId" -> queueId,
        "spotifyArtistCode" -> spotifyArtistCode,
        "syncScope" -> syncScope,
        "expectedStatus" -> expectedStatus.dbValue,
        "expectedLockToken" -> expectedLockToken,
        "expectedQueueLockVersion" -> expectedQueueLockVersion,
        "expectedDeleted" -> expectedDeleted,
        "status" -> status.dbValue,
        "includeGroups" -> includeGroups,
        "market" -> market,
        "requestedLimit" -> requestedLimit,
        "nextOffset" -> nextOffset,
        "nextAttemptAt" -> nextAttemptAt.map(_.toLocalDateTime),
        "lastAttemptedAt" -> lastAttemptedAt.map(_.toLocalDateTime),
        "completedAt" -> completedAt.map(_.toLocalDateTime),
        "attemptCount" -> attemptCount,
        "lastFailedAt" -> lastFailedAt.map(_.toLocalDateTime),
        "lastErrorType" -> lastErrorType,
        "lockToken" -> lockToken,
        "lockedUntil" -> lockedUntil.map(_.toLocalDateTime),
        "updatedAt" -> updatedAt.toLocalDateTime,
        "deletedAt" -> deletedAt.map(_.toLocalDateTime),
        "updatedUser" -> updatedUser.dbValue,
        "deletedUser" -> deletedUser.dbValue,
        "deleted" -> deleted,
        "lockVersion" -> lockVersion
      )
      .update
      .apply() == 1
}
