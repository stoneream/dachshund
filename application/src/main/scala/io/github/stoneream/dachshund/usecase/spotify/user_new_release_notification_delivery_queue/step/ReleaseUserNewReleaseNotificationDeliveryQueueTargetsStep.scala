package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.UserNewReleaseNotificationDeliveryQueueService
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.model.UserNewReleaseNotificationDeliveryQueueTarget

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
private[user_new_release_notification_delivery_queue] class ReleaseUserNewReleaseNotificationDeliveryQueueTargetsStep @Inject() (
    queueService: UserNewReleaseNotificationDeliveryQueueService
) extends TraceLogger {
  def run(
      targets: Seq[UserNewReleaseNotificationDeliveryQueueTarget],
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[Int] =
    queueService
      .releaseProcessingTargets(targets, now)
      .recoverWith { case NonFatal(releaseException) =>
        warn(
          "ユーザー別新着リリース通知配信の abort 後 release に失敗しました",
          kv("failureClass", releaseException.getClass.getName),
          kv("originalFailureClass", exception.getClass.getName)
        )
        Future.successful(0)
      }
}
