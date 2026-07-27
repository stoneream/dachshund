package io.github.stoneream.dachshund.usecase.job_status.user_new_release_events_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.job_status.UserNewReleaseEventsSyncJobStatusReader
import io.github.stoneream.dachshund.infra.db.reader.job_status.UserNewReleaseEventsSyncJobStatusReader.EventRow as ReaderEventRow
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.job_status.detail.JobStatusDetailUseCaseException

import scala.concurrent.Future

@Singleton
class UserNewReleaseEventsSyncJobStatusUseCase @Inject() (
    databaseTransaction: DatabaseTransaction,
    reader: UserNewReleaseEventsSyncJobStatusReader,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UserNewReleaseEventsSyncJobStatusUseCaseInput,
      UserNewReleaseEventsSyncJobStatusUseCaseOutput,
      JobStatusDetailUseCaseException
    ] {
  override def run(input: UserNewReleaseEventsSyncJobStatusUseCaseInput)(using
      LoggingContext
  ): Future[UserNewReleaseEventsSyncJobStatusUseCaseOutput] =
    show(input)

  private def show(input: UserNewReleaseEventsSyncJobStatusUseCaseInput): Future[UserNewReleaseEventsSyncJobStatusUseCaseOutput] =
    readStatus(input).map { case (totalRows, rows, detailPage) =>
      UserNewReleaseEventsSyncJobStatusUseCaseOutput(
        context = UserNewReleaseEventsSyncJobStatusPageContext.build(
          userDisplayName = input.user.displayName,
          eventRows = rows.map(eventRow),
          detailPage = detailPage,
          detailLimit = input.detailLimit,
          totalRows = totalRows
        )
      )
    }(using defaultExecutor)

  private def readStatus(input: UserNewReleaseEventsSyncJobStatusUseCaseInput) =
    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        val totalRows = reader.countEvents()
        val detailPage = UserNewReleaseEventsSyncJobStatusPageContext.calculateEffectivePage(input.detailPage, input.detailLimit, totalRows)
        val offset = UserNewReleaseEventsSyncJobStatusPageContext.calculateOffset(detailPage, input.detailLimit)

        (totalRows, reader.findEventRows(input.detailLimit, offset), detailPage)
      }
    }(using databaseExecutor)

  private def eventRow(row: ReaderEventRow): UserNewReleaseEventsSyncJobStatusPageContext.EventRow =
    UserNewReleaseEventsSyncJobStatusPageContext.EventRow(
      eventId = row.eventId,
      userId = row.userId,
      artistReleaseId = row.artistReleaseId,
      spotifyReleaseCode = row.spotifyReleaseCode,
      sourceSpotifyArtistCode = row.sourceSpotifyArtistCode,
      notificationQueueId = row.notificationQueueId,
      notificationStatus = row.notificationStatus,
      notificationAttemptCount = row.notificationAttemptCount,
      notificationNextAttemptAt = row.notificationNextAttemptAt,
      notificationLastAttemptedAt = row.notificationLastAttemptedAt,
      notificationCompletedAt = row.notificationCompletedAt,
      notificationLastFailedAt = row.notificationLastFailedAt,
      notificationLastErrorType = row.notificationLastErrorType,
      notificationLockedUntil = row.notificationLockedUntil,
      detectedAt = row.detectedAt,
      createdAt = row.createdAt,
      updatedAt = row.updatedAt
    )
}
