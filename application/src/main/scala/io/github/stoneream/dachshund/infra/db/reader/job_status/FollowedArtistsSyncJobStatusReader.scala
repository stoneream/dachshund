package io.github.stoneream.dachshund.infra.db.reader.job_status

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import scalikejdbc.*

object FollowedArtistsSyncJobStatusReader {
  final case class StatusCount(
      status: QueueJobStatus,
      count: Long
  )

  final case class QueueRow(
      queueId: Long,
      status: QueueJobStatus,
      targetLabel: String,
      attemptCount: Int,
      nextAttemptAt: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockedUntil: Option[BusinessDateTime],
      createdAt: BusinessDateTime,
      updatedAt: BusinessDateTime
  )
}

@Singleton
class FollowedArtistsSyncJobStatusReader @Inject() () {
  import FollowedArtistsSyncJobStatusReader.{QueueRow, StatusCount}

  def countByStatus()(using DBSession): Seq[StatusCount] =
    sql"""
      select
        q.status,
        count(*) as queue_count
      from
        followed_artist_sync_queue q
        inner join user u
          on u.id = q.user_id
          and u.deleted = 0
          and u.enabled = 1
      where
        q.deleted = 0
      group by
        q.status
    """
      .map(statusCount)
      .list
      .apply()

  def findQueueRows(
      selectedStatuses: Set[QueueJobStatus],
      limit: Int,
      offset: Long
  )(using DBSession): Seq[QueueRow] =
    sql"""
      select
        q.id as queue_id,
        q.user_id,
        q.sync_date,
        q.status,
        q.next_attempt_at,
        q.attempt_count,
        q.last_failed_at,
        q.last_error_type,
        q.locked_until,
        q.last_attempted_at,
        q.completed_at,
        q.created_at,
        q.updated_at
      from
        followed_artist_sync_queue q
        inner join user u
          on u.id = q.user_id
          and u.deleted = 0
          and u.enabled = 1
      where
        q.deleted = 0
        and (
          ({scheduledSelected} = 1 and q.status = {scheduledStatus})
          or ({processingSelected} = 1 and q.status = {processingStatus})
          or ({succeededSelected} = 1 and q.status = {succeededStatus})
          or ({failedSelected} = 1 and q.status = {failedStatus})
          or ({blockedSelected} = 1 and q.status = {blockedStatus})
          or ({skippedSelected} = 1 and q.status = {skippedStatus})
        )
      order by
        q.updated_at desc,
        q.id desc
      limit {limit}
      offset {offset}
    """
      .bindByName(bindStatusFilter(selectedStatuses, limit, offset)*)
      .map { row =>
        queueRow(
          row,
          targetLabel = s"user_id=${row.long("user_id")}, sync_date=${row.localDate("sync_date")}"
        )
      }
      .list
      .apply()

  private def statusCount(rs: WrappedResultSet): StatusCount =
    StatusCount(
      status = QueueJobStatus.fromDbValue(rs.string("status")),
      count = rs.long("queue_count")
    )

  private def queueRow(rs: WrappedResultSet, targetLabel: String): QueueRow =
    QueueRow(
      queueId = rs.long("queue_id"),
      status = QueueJobStatus.fromDbValue(rs.string("status")),
      targetLabel = targetLabel,
      attemptCount = rs.int("attempt_count"),
      nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
      lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
      completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
      lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
      lastErrorType = rs.string("last_error_type"),
      lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
      createdAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("created_at")),
      updatedAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("updated_at"))
    )

  private def bindStatusFilter(selectedStatuses: Set[QueueJobStatus], limit: Int, offset: Long): Seq[(String, Any)] =
    Seq(
      "scheduledSelected" -> selected(selectedStatuses, QueueJobStatus.Scheduled),
      "processingSelected" -> selected(selectedStatuses, QueueJobStatus.Processing),
      "succeededSelected" -> selected(selectedStatuses, QueueJobStatus.Succeeded),
      "failedSelected" -> selected(selectedStatuses, QueueJobStatus.Failed),
      "blockedSelected" -> selected(selectedStatuses, QueueJobStatus.Blocked),
      "skippedSelected" -> selected(selectedStatuses, QueueJobStatus.Skipped),
      "scheduledStatus" -> QueueJobStatus.Scheduled.dbValue,
      "processingStatus" -> QueueJobStatus.Processing.dbValue,
      "succeededStatus" -> QueueJobStatus.Succeeded.dbValue,
      "failedStatus" -> QueueJobStatus.Failed.dbValue,
      "blockedStatus" -> QueueJobStatus.Blocked.dbValue,
      "skippedStatus" -> QueueJobStatus.Skipped.dbValue,
      "limit" -> limit,
      "offset" -> offset
    )

  private def selected(selectedStatuses: Set[QueueJobStatus], status: QueueJobStatus): Int =
    if (selectedStatuses.contains(status)) 1 else 0
}
