package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model.UserNewReleaseNotificationQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context.UserNewReleaseNotificationDeliveryResult

import scala.concurrent.Future

@Singleton
private[user_new_release_notification_delivery] class SyncUserNewReleaseNotificationDeliveryTargetsStep @Inject() () extends TraceLogger {
  def run(
      targets: Seq[UserNewReleaseNotificationQueueTarget]
  )(
      syncTarget: UserNewReleaseNotificationQueueTarget => Future[UserNewReleaseNotificationDeliveryResult]
  )(using LoggingContext, DefaultExecutor): Future[Unit] =
    targets.foldLeft(Future.successful(())) { (futureDone, target) =>
      for {
        _ <- futureDone
        result <- syncTarget(target)
      } yield logProgress(target, result, targets.size)
    }

  private def logProgress(
      target: UserNewReleaseNotificationQueueTarget,
      result: UserNewReleaseNotificationDeliveryResult,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "ユーザー別新着リリース通知配信を処理中です",
      kv("userNewReleaseNotificationQueueId", target.queueId),
      kv("userId", target.userId),
      kv("artistReleaseId", target.artistReleaseId),
      kv("userNewReleaseNotificationDelivery.result", result),
      kv("userNewReleaseNotificationDelivery.selectedCount", selectedCount)
    )
}
