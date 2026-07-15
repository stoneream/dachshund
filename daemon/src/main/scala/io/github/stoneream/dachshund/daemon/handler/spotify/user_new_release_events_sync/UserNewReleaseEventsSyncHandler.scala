package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_events_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.UserNewReleaseEventsSyncJobConfig
import io.github.stoneream.dachshund.daemon.job.JobHandler
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.{UserNewReleaseEventsSyncUseCase, UserNewReleaseEventsSyncUseCaseInput as UseCaseInput}
import zio.{Task, ZIO}

@Singleton
class UserNewReleaseEventsSyncHandler @Inject() (
    useCase: UserNewReleaseEventsSyncUseCase,
    dateTimeService: DateTimeService,
    config: UserNewReleaseEventsSyncJobConfig
) extends JobHandler {
  override def handle()(using LoggingContext): Task[Unit] =
    ZIO
      .fromFuture(_ =>
        useCase.run(
          UseCaseInput(
            now = dateTimeService.now(),
            batchSize = config.batchSize
          )
        )
      )
      .unit
}
