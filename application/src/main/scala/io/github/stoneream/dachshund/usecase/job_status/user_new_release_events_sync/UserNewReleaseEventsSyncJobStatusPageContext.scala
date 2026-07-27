package io.github.stoneream.dachshund.usecase.job_status.user_new_release_events_sync

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.usecase.job_status.context.{JobStatusJob, JobStatusJobOption}

final case class UserNewReleaseEventsSyncJobStatusPageContext(
    user: UserNewReleaseEventsSyncJobStatusPageContext.ViewUser,
    currentJob: JobStatusJobOption,
    eventRows: Seq[UserNewReleaseEventsSyncJobStatusPageContext.EventRow],
    pagination: UserNewReleaseEventsSyncJobStatusPageContext.Pagination,
    detailLimit: Int
)

object UserNewReleaseEventsSyncJobStatusPageContext {
  final case class ViewUser(
      displayName: String
  )

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

  def build(
      userDisplayName: String,
      eventRows: Seq[EventRow],
      detailPage: Int,
      detailLimit: Int,
      totalRows: Long
  ): UserNewReleaseEventsSyncJobStatusPageContext = {
    val currentPage = calculateEffectivePage(detailPage, detailLimit, totalRows)
    val totalPages = calculateTotalPages(totalRows, detailLimit)
    val currentJob = JobStatusJob.UserNewReleaseEventsSync

    UserNewReleaseEventsSyncJobStatusPageContext(
      user = ViewUser(userDisplayName),
      currentJob = JobStatusJobOption.fromJob(currentJob),
      eventRows = eventRows,
      pagination = pagination(
        currentJob = currentJob,
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

  private def pagination(
      currentJob: JobStatusJob,
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
      previousPath = Option.when(currentPage > 1)(pagePath(currentJob, currentPage - 1)),
      nextPath = Option.when(currentPage < totalPages)(pagePath(currentJob, currentPage + 1)),
      pageItems = pageItems(currentJob, currentPage, totalPages)
    )

  private def pageItems(
      currentJob: JobStatusJob,
      currentPage: Int,
      totalPages: Int
  ): Seq[PageItem] =
    pageNumbers(currentPage, totalPages).map {
      case Some(page) if page == currentPage =>
        PageItem(label = page.toString, path = None, current = true)
      case Some(page) =>
        PageItem(label = page.toString, path = Some(pagePath(currentJob, page)), current = false)
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

  private def pagePath(job: JobStatusJob, page: Int): String =
    s"/job/status/${job.name}?page=$page"
}
