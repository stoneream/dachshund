package io.github.stoneream.dachshund.infra.db.reader.user_new_release_events_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseEventSource
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import scalikejdbc.*

import java.time.LocalDateTime

@Singleton
class UserNewReleaseEventsReader @Inject() () {
  def read(
      releasedFrom: LocalDateTime,
      releasedTo: LocalDateTime,
      batchSize: Int,
      detectedAt: BusinessDateTime,
      detectionSyncCode: String
  )(using DBSession): Seq[UserNewReleaseEventSource] = {
    val query = sql"""
      select
        ufa.user_id,
        ar.id as artist_release_id,
        ar.spotify_release_code,
        ar.source_spotify_artist_code
      from
        artist_release ar
        inner join user_followed_artist ufa
          on ufa.spotify_artist_code = ar.source_spotify_artist_code
          and ufa.deleted = 0
          and ufa.first_followed_at is not null
        left join blocked_label bl
          on bl.user_id = ufa.user_id
          and bl.normalized_label_name = ar.normalized_label_name
          and bl.enabled = 1
          and bl.deleted = 0
        left join user_new_release_event existing
          on existing.user_id = ufa.user_id
          and existing.artist_release_id = ar.id
      where
        ar.deleted = 0
        and ar.release_date_precision = 'day'
        and ar.release_date_at is not null
        and ar.release_date_at >= ${releasedFrom}
        and ar.release_date_at <= ${releasedTo}
        and (
          ar.normalized_label_name is null
          or bl.id is null
        )
        and existing.id is null
      order by
        ar.release_date_at desc,
        ar.id asc,
        ufa.user_id asc
      limit ${batchSize}
    """

    query
      .map { rs =>
        UserNewReleaseEventSource(
          userId = rs.long("user_id"),
          artistReleaseId = rs.long("artist_release_id"),
          spotifyReleaseCode = rs.string("spotify_release_code"),
          sourceSpotifyArtistCode = rs.string("source_spotify_artist_code"),
          detectedAt = detectedAt,
          detectionSyncCode = detectionSyncCode,
          createdAt = detectedAt,
          updatedAt = detectedAt,
          deletedAt = Option.empty,
          createdUser = AuditUser.System,
          updatedUser = AuditUser.System,
          deletedUser = AuditUser.Empty,
          deleted = 0L,
          lockVersion = 0L
        )
      }
      .list
      .apply()
  }
}
