package io.github.stoneream.dachshund.infra.db.writer

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.generated.UserNewReleaseEventDbRow
import scalikejdbc.*

@Singleton
class UserNewReleaseEventsWriter @Inject() () {
  def write(row: UserNewReleaseEventDbRow)(using DBSession): Int =
    sql"""
      insert ignore into user_new_release_event (
        user_id,
        artist_release_id,
        spotify_release_code,
        source_spotify_artist_code,
        detected_at,
        detection_sync_code,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      )
      values (
        {userId},
        {artistReleaseId},
        {spotifyReleaseCode},
        {sourceSpotifyArtistCode},
        {detectedAt},
        {detectionSyncCode},
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
        "artistReleaseId" -> row.artistReleaseId,
        "spotifyReleaseCode" -> row.spotifyReleaseCode,
        "sourceSpotifyArtistCode" -> row.sourceSpotifyArtistCode,
        "detectedAt" -> row.detectedAt,
        "detectionSyncCode" -> row.detectionSyncCode,
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
