package io.github.stoneream.dachshund.infra.db.reader.job_status

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

object UserNewReleaseEventsSyncJobStatusReader {
  private val DetectionSyncCode = "user-new-release-events-sync"

  final case class EventRow(
      eventId: Long,
      userId: Long,
      artistReleaseId: Long,
      spotifyReleaseCode: String,
      sourceSpotifyArtistCode: String,
      notificationQueueId: Option[Long],
      notificationStatus: Option[QueueJobStatus],
      notificationAttemptCount: Option[Int],
      notificationNextAttemptAt: Option[BusinessDateTime],
      notificationLastAttemptedAt: Option[BusinessDateTime],
      notificationCompletedAt: Option[BusinessDateTime],
      notificationLastFailedAt: Option[BusinessDateTime],
      notificationLastErrorType: Option[String],
      notificationLockedUntil: Option[BusinessDateTime],
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
        q.id as notification_queue_id,
        q.status as notification_status,
        q.next_attempt_at as notification_next_attempt_at,
        q.attempt_count as notification_attempt_count,
        q.last_failed_at as notification_last_failed_at,
        q.last_error_type as notification_last_error_type,
        q.locked_until as notification_locked_until,
        q.last_attempted_at as notification_last_attempted_at,
        q.completed_at as notification_completed_at,
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
        left join user_new_release_notification_queue q
          on q.user_new_release_event_id = e.id
          and q.deleted = 0
          and exists (
            select
              1
            from
              user_playlist_setting ups
            where
              ups.id = q.playlist_setting_id
              and ups.deleted = 0
              and ups.enabled = 1
          )
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
      notificationQueueId = rs.longOpt("notification_queue_id"),
      notificationStatus = rs.stringOpt("notification_status").map(QueueJobStatus.fromDbValue),
      notificationAttemptCount = rs.intOpt("notification_attempt_count"),
      notificationNextAttemptAt = rs.localDateTimeOpt("notification_next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
      notificationLastAttemptedAt = rs.localDateTimeOpt("notification_last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
      notificationCompletedAt = rs.localDateTimeOpt("notification_completed_at").map(BusinessDateTime.fromLocalDateTime),
      notificationLastFailedAt = rs.localDateTimeOpt("notification_last_failed_at").map(BusinessDateTime.fromLocalDateTime),
      notificationLastErrorType = rs.stringOpt("notification_last_error_type"),
      notificationLockedUntil = rs.localDateTimeOpt("notification_locked_until").map(BusinessDateTime.fromLocalDateTime),
      detectedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("detected_at")),
      createdAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("created_at")),
      updatedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("updated_at"))
    )
}
