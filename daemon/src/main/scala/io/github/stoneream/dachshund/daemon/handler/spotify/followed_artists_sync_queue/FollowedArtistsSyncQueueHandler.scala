package io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync_queue

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.job.JobHandler
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync_queue.{FollowedArtistsSyncQueueUseCase, FollowedArtistsSyncQueueUseCaseInput as UseCaseInput}
import zio.{Task, ZIO}

@Singleton
class FollowedArtistsSyncQueueHandler @Inject() (
    useCase: FollowedArtistsSyncQueueUseCase,
    dateTimeService: DateTimeService
) extends JobHandler {
  override def handle()(using LoggingContext): Task[Unit] =
    ZIO
      .fromFuture(_ =>
        useCase.run(
          UseCaseInput(
            now = dateTimeService.now()
          )
        )
      )
      .unit
}
