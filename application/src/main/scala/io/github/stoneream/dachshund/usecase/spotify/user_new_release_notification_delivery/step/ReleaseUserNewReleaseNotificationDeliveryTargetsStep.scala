package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.UserNewReleaseNotificationQueueService
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model.UserNewReleaseNotificationQueueTarget

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
private[user_new_release_notification_delivery] class ReleaseUserNewReleaseNotificationDeliveryTargetsStep @Inject() (
    queueService: UserNewReleaseNotificationQueueService
) extends TraceLogger {
  def run(
      targets: Seq[UserNewReleaseNotificationQueueTarget],
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
