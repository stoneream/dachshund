package io.github.stoneream.dachshund.usecase.job_status.detail

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.usecase.job_status.context.{JobStatusJob, JobStatusJobOption}

final case class JobStatusDetailPageContext(
    user: JobStatusDetailPageContext.ViewUser,
    currentJob: JobStatusJobOption,
    jobOptions: Seq[JobStatusJobOption],
    statusFilters: Seq[JobStatusDetailPageContext.StatusFilter],
    statusSummaries: Seq[JobStatusDetailPageContext.StatusSummary],
    queueRows: Seq[JobStatusDetailPageContext.QueueRow],
    pagination: JobStatusDetailPageContext.Pagination,
    detailLimit: Int
)

object JobStatusDetailPageContext {
  final case class ViewUser(
      displayName: String
  )

  final case class StatusFilter(
      status: QueueJobStatus,
      label: String,
      selected: Boolean
  )

  final case class StatusSummary(
      status: QueueJobStatus,
      label: String,
      count: Long
  )

  final case class Pagination(
      currentPage: Int,
      totalPages: Int,
      totalRows: Long,
      pageSize: Int,
      previousPath: Option[String],
      nextPath: Option[String],
      pageItems: Seq[PageItem]
  )

  final case class PageItem(
      label: String,
      path: Option[String],
      current: Boolean
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

  def build(
      userDisplayName: String,
      currentJob: JobStatusJob,
      selectedStatuses: Set[QueueJobStatus],
      statusCounts: Seq[(QueueJobStatus, Long)],
      queueRows: Seq[QueueRow],
      detailPage: Int,
      detailLimit: Int
  ): JobStatusDetailPageContext = {
    val countsByStatus = statusCounts.toMap
    val totalRows = selectedStatuses.toSeq.map(status => countsByStatus.getOrElse(status, 0L)).sum
    val totalPages = calculateTotalPages(totalRows, detailLimit)
    val currentPage = calculateEffectivePage(detailPage, detailLimit, totalRows)

    JobStatusDetailPageContext(
      user = ViewUser(userDisplayName),
      currentJob = JobStatusJobOption.fromJob(currentJob),
      jobOptions = JobStatusJob.All.map(JobStatusJobOption.fromJob),
      statusFilters = QueueJobStatus.values.toSeq.map { status =>
        StatusFilter(
          status = status,
          label = statusLabel(status),
          selected = selectedStatuses.contains(status)
        )
      },
      statusSummaries = QueueJobStatus.values.toSeq.map { status =>
        StatusSummary(
          status = status,
          label = statusLabel(status),
          count = countsByStatus.getOrElse(status, 0L)
        )
      },
      queueRows = queueRows,
      pagination = pagination(
        currentJob = currentJob,
        selectedStatuses = selectedStatuses,
        currentPage = currentPage,
        totalPages = totalPages,
        totalRows = totalRows,
        pageSize = detailLimit
      ),
      detailLimit = detailLimit
    )
  }

  def calculateTotalPages(totalRows: Long, pageSize: Int): Int =
    if (totalRows <= 0L) {
      1
    } else {
      ((totalRows + pageSize - 1L) / pageSize).min(Int.MaxValue.toLong).toInt
    }

  def calculateEffectivePage(requestedPage: Int, pageSize: Int, totalRows: Long): Int =
    requestedPage.max(1).min(calculateTotalPages(totalRows, pageSize))

  def calculateOffset(page: Int, pageSize: Int): Long =
    (page - 1).toLong * pageSize.toLong

  def statusLabel(status: QueueJobStatus): String =
    status match {
      case QueueJobStatus.Scheduled => "実行待ち"
      case QueueJobStatus.Processing => "処理中"
      case QueueJobStatus.Succeeded => "完了"
      case QueueJobStatus.Failed => "失敗"
      case QueueJobStatus.Blocked => "要対応"
      case QueueJobStatus.Skipped => "スキップ済み"
    }

  private def pagination(
      currentJob: JobStatusJob,
      selectedStatuses: Set[QueueJobStatus],
      currentPage: Int,
      totalPages: Int,
      totalRows: Long,
      pageSize: Int
  ): Pagination =
    Pagination(
      currentPage = currentPage,
      totalPages = totalPages,
      totalRows = totalRows,
      pageSize = pageSize,
      previousPath = Option.when(currentPage > 1)(pagePath(currentJob, selectedStatuses, currentPage - 1)),
      nextPath = Option.when(currentPage < totalPages)(pagePath(currentJob, selectedStatuses, currentPage + 1)),
      pageItems = pageItems(currentJob, selectedStatuses, currentPage, totalPages)
    )

  private def pageItems(
      currentJob: JobStatusJob,
      selectedStatuses: Set[QueueJobStatus],
      currentPage: Int,
      totalPages: Int
  ): Seq[PageItem] =
    pageNumbers(currentPage, totalPages).map {
      case Some(page) if page == currentPage =>
        PageItem(label = page.toString, path = None, current = true)
      case Some(page) =>
        PageItem(label = page.toString, path = Some(pagePath(currentJob, selectedStatuses, page)), current = false)
      case None =>
        PageItem(label = "...", path = None, current = false)
    }

  private def pageNumbers(currentPage: Int, totalPages: Int): Seq[Option[Int]] =
    if (totalPages <= 7) {
      (1 to totalPages).map(Some(_))
    } else {
      val window = ((currentPage - 2).max(2) to (currentPage + 2).min(totalPages - 1)).map(Some(_))
      Seq(Some(1)) ++ ellipsisBefore(window) ++ window ++ ellipsisAfter(window, totalPages) ++ Seq(Some(totalPages))
    }

  private def ellipsisBefore(window: Seq[Option[Int]]): Seq[Option[Int]] =
    if (window.headOption.flatten.exists(_ > 2)) Seq(None) else Seq.empty

  private def ellipsisAfter(window: Seq[Option[Int]], totalPages: Int): Seq[Option[Int]] =
    if (window.lastOption.flatten.exists(_ < totalPages - 1)) Seq(None) else Seq.empty

  private def pagePath(job: JobStatusJob, selectedStatuses: Set[QueueJobStatus], page: Int): String = {
    val statusParams =
      if (selectedStatuses == QueueJobStatus.values.toSet) {
        Seq.empty
      } else {
        QueueJobStatus.values.toSeq
          .filter(selectedStatuses.contains)
          .map(status => s"status=${status.dbValue}")
      }
    val params = statusParams :+ s"page=$page"

    s"/job/status/${job.name}?${params.mkString("&")}"
  }
}
