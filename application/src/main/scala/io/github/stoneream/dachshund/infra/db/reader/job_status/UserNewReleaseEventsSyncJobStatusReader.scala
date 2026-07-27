package io.github.stoneream.dachshund.infra.db.reader.job_status

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import scalikejdbc.*

object UserNewReleaseEventsSyncJobStatusReader {
  private val DetectionSyncCode = "user-new-release-events-sync"

  final case class EventRow(
      eventId: Long,
      userId: Long,
      artistReleaseId: Long,
      spotifyReleaseCode: String,
      sourceSpotifyArtistCode: String,
      detectedAt: BusinessDateTime,
      createdAt: BusinessDateTime,
      updatedAt: BusinessDateTime
  )
}

@Singleton
class UserNewReleaseEventsSyncJobStatusReader @Inject() () {
  import UserNewReleaseEventsSyncJobStatusReader.*

  def countEvents()(using DBSession): Long =
    sql"""
      select
        count(*) as event_count
      from
        user_new_release_event e
        inner join user u
          on u.id = e.user_id
          and u.deleted = 0
          and u.enabled = 1
        inner join artist_release ar
          on ar.id = e.artist_release_id
          and ar.deleted = 0
      where
        e.deleted = 0
        and e.detection_sync_code = {detectionSyncCode}
    """
      .bindByName("detectionSyncCode" -> DetectionSyncCode)
      .map(_.long("event_count"))
      .single
      .apply()
      .getOrElse(0L)

  def findEventRows(limit: Int, offset: Long)(using DBSession): Seq[EventRow] =
    sql"""
      select
        e.id as event_id,
        e.user_id,
        e.artist_release_id,
        e.spotify_release_code,
        e.source_spotify_artist_code,
        e.detected_at,
        e.created_at,
        e.updated_at
      from
        user_new_release_event e
        inner join user u
          on u.id = e.user_id
          and u.deleted = 0
          and u.enabled = 1
        inner join artist_release ar
          on ar.id = e.artist_release_id
          and ar.deleted = 0
      where
        e.deleted = 0
        and e.detection_sync_code = {detectionSyncCode}
      order by
        e.detected_at desc,
        e.id desc
      limit {limit}
      offset {offset}
    """
      .bindByName(
        "detectionSyncCode" -> DetectionSyncCode,
        "limit" -> limit,
        "offset" -> offset
      )
      .map(eventRow)
      .list
      .apply()

  private def eventRow(rs: WrappedResultSet): EventRow =
    EventRow(
      eventId = rs.long("event_id"),
      userId = rs.long("user_id"),
      artistReleaseId = rs.long("artist_release_id"),
      spotifyReleaseCode = rs.string("spotify_release_code"),
      sourceSpotifyArtistCode = rs.string("source_spotify_artist_code"),
      detectedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("detected_at")),
      createdAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("created_at")),
      updatedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("updated_at"))
    )
}
