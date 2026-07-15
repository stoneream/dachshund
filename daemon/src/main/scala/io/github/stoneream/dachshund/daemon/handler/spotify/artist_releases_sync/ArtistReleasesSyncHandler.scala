package io.github.stoneream.dachshund.daemon.handler.spotify.artist_releases_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.ArtistReleasesSyncJobConfig
import io.github.stoneream.dachshund.daemon.job.JobHandler
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.{ArtistReleasesSyncUseCase, ArtistReleasesSyncUseCaseInput as UseCaseInput}
import zio.{Task, ZIO}

@Singleton
class ArtistReleasesSyncHandler @Inject() (
    useCase: ArtistReleasesSyncUseCase,
    dateTimeService: DateTimeService,
    config: ArtistReleasesSyncJobConfig
) extends JobHandler {
  override def handle()(using LoggingContext): Task[Unit] =
    ZIO
      .fromFuture(_ =>
        useCase.run(
          UseCaseInput(
            now = dateTimeService.now(),
            batchSize = config.batchSize,
            processingLease = config.processingLease
          )
        )
      )
      .unit
}
