package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_notification_delivery_queue

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.UserNewReleaseNotificationDeliveryQueueJobConfig
import io.github.stoneream.dachshund.daemon.job.JobHandler
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.{UserNewReleaseNotificationDeliveryQueueUseCase, UserNewReleaseNotificationDeliveryQueueUseCaseInput as UseCaseInput}
import zio.{Task, ZIO}

@Singleton
class UserNewReleaseNotificationDeliveryQueueHandler @Inject() (
    useCase: UserNewReleaseNotificationDeliveryQueueUseCase,
    dateTimeService: DateTimeService,
    config: UserNewReleaseNotificationDeliveryQueueJobConfig
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
