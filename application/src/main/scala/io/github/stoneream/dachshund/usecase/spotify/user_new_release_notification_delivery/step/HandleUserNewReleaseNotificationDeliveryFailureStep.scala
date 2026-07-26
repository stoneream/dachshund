package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.{UserNewReleaseNotificationQueueService, UserNewReleaseNotificationQueueUpdateResult}
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model.UserNewReleaseNotificationQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context.UserNewReleaseNotificationDeliveryResult.{Blocked, StaleLockSkipped, TemporaryFailure}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context.{UserNewReleaseNotificationDeliveryFailure, UserNewReleaseNotificationDeliveryFailureType, UserNewReleaseNotificationDeliveryResult}

import scala.concurrent.Future

@Singleton
private[user_new_release_notification_delivery] class HandleUserNewReleaseNotificationDeliveryFailureStep @Inject() (
    applicationConfig: ApplicationConfig,
    queueService: UserNewReleaseNotificationQueueService
) {
  def run(
      target: UserNewReleaseNotificationQueueTarget,
      failure: UserNewReleaseNotificationDeliveryFailure,
      now: BusinessDateTime
  )(using DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    if (requiresOperationAction(failure.failureType)) {
      queueService
        .markBlocked(target, failure.failureType, now)
        .map(queueUpdateResult(_, Blocked))
    } else {
      val nextAttemptAt = CalculateNextUserNewReleaseNotificationDeliveryAttemptAt(
        now = now,
        failureCount = target.attemptCount,
        failure = failure,
        retryConfig = applicationConfig.spotify.client.retry
      )
      queueService
        .markTemporaryFailure(target, failure.failureType, nextAttemptAt, now)
        .map(queueUpdateResult(_, TemporaryFailure))
    }

  private def requiresOperationAction(failureType: String): Boolean =
    failureType == UserNewReleaseNotificationDeliveryFailureType.AuthorizationNotFound ||
      failureType == UserNewReleaseNotificationDeliveryFailureType.InsufficientScope ||
      failureType == UserNewReleaseNotificationDeliveryFailureType.PlaylistClientError ||
      failureType == UserNewReleaseNotificationDeliveryFailureType.ReleaseTracksNotFound ||
      failureType == "invalid_grant" ||
      failureType == "token_decrypt_failed" ||
      failureType == "insufficient_scope"

  private def queueUpdateResult(
      result: UserNewReleaseNotificationQueueUpdateResult,
      updatedResult: UserNewReleaseNotificationDeliveryResult
  ): UserNewReleaseNotificationDeliveryResult =
    result match {
      case UserNewReleaseNotificationQueueUpdateResult.Updated => updatedResult
      case UserNewReleaseNotificationQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
    }
}
