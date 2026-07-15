package io.github.stoneream.dachshund.daemon.handler.spotify

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.SpotifyAccessTokenRefreshJobConfig
import io.github.stoneream.dachshund.daemon.job.JobHandler
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.{SpotifyAccessTokenRefreshUseCase, SpotifyAccessTokenRefreshUseCaseInput}
import zio.{Task, ZIO}

@Singleton
class SpotifyAccessTokenRefreshJobHandler @Inject() (
    useCase: SpotifyAccessTokenRefreshUseCase,
    dateTimeService: DateTimeService,
    config: SpotifyAccessTokenRefreshJobConfig
) extends JobHandler {
  override def handle()(using LoggingContext): Task[Unit] =
    ZIO
      .fromFuture(_ =>
        useCase.run(
          SpotifyAccessTokenRefreshUseCaseInput(
            now = dateTimeService.now(),
            batchSize = config.batchSize
          )
        )
      )
      .unit
}
