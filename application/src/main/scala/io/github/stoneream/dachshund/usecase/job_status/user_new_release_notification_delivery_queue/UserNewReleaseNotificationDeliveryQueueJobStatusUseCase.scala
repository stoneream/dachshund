package io.github.stoneream.dachshund.usecase.job_status.user_new_release_notification_delivery_queue

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.job_status.UserNewReleaseNotificationDeliveryQueueJobStatusReader
import io.github.stoneream.dachshund.infra.db.reader.job_status.UserNewReleaseNotificationDeliveryQueueJobStatusReader.QueueRow as ReaderQueueRow
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailPageContext, JobStatusDetailUseCaseException, JobStatusDetailUseCaseInput}
import io.github.stoneream.dachshund.usecase.job_status.context.JobStatusJob

import scala.concurrent.Future

@Singleton
class UserNewReleaseNotificationDeliveryQueueJobStatusUseCase @Inject() (
    databaseTransaction: DatabaseTransaction,
    reader: UserNewReleaseNotificationDeliveryQueueJobStatusReader,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      JobStatusDetailUseCaseInput,
      UserNewReleaseNotificationDeliveryQueueJobStatusUseCaseOutput,
      JobStatusDetailUseCaseException
    ] {
  override def run(input: JobStatusDetailUseCaseInput)(using LoggingContext): Future[UserNewReleaseNotificationDeliveryQueueJobStatusUseCaseOutput] =
    show(input)

  private def show(input: JobStatusDetailUseCaseInput): Future[UserNewReleaseNotificationDeliveryQueueJobStatusUseCaseOutput] =
    readStatus(input).map { case (counts, rows, detailPage) =>
      UserNewReleaseNotificationDeliveryQueueJobStatusUseCaseOutput(
        context = JobStatusDetailPageContext.build(
          userDisplayName = input.user.displayName,
          currentJob = JobStatusJob.UserNewReleaseNotificationDeliveryQueue,
          selectedStatuses = input.selectedStatuses,
          statusCounts = counts.map(row => row.status -> row.count),
          queueRows = rows.map(queueRow),
          detailPage = detailPage,
          detailLimit = input.detailLimit
        )
      )
    }(using defaultExecutor)

  private def readStatus(input: JobStatusDetailUseCaseInput) =
    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        val counts = reader.countByStatus()
        val countsByStatus = counts.map(row => row.status -> row.count).toMap
        val totalRows = input.selectedStatuses.toSeq.map(status => countsByStatus.getOrElse(status, 0L)).sum
        val detailPage = JobStatusDetailPageContext.calculateEffectivePage(input.detailPage, input.detailLimit, totalRows)
        val offset = JobStatusDetailPageContext.calculateOffset(detailPage, input.detailLimit)

        (counts, reader.findQueueRows(input.selectedStatuses, input.detailLimit, offset), detailPage)
      }
    }(using databaseExecutor)

  private def queueRow(row: ReaderQueueRow): JobStatusDetailPageContext.QueueRow =
    JobStatusDetailPageContext.QueueRow(
      queueId = row.queueId,
      status = row.status,
      targetLabel = row.targetLabel,
      attemptCount = row.attemptCount,
      nextAttemptAt = row.nextAttemptAt,
      lastAttemptedAt = row.lastAttemptedAt,
      completedAt = row.completedAt,
      lastFailedAt = row.lastFailedAt,
      lastErrorType = row.lastErrorType,
      lockedUntil = row.lockedUntil,
      createdAt = row.createdAt,
      updatedAt = row.updatedAt
    )
}
