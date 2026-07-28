package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.{UserNewReleaseNotificationDeliveryQueueService, UserNewReleaseNotificationDeliveryQueueUpdateResult}
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.model.UserNewReleaseNotificationDeliveryQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.UserNewReleaseNotificationDeliveryQueueResult.{Blocked, StaleLockSkipped, TemporaryFailure}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.{UserNewReleaseNotificationDeliveryQueueFailure, UserNewReleaseNotificationDeliveryQueueFailureType, UserNewReleaseNotificationDeliveryQueueResult}

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
private[user_new_release_notification_delivery_queue] class HandleUserNewReleaseNotificationDeliveryQueueFailureStep @Inject() (
    applicationConfig: ApplicationConfig,
    queueService: UserNewReleaseNotificationDeliveryQueueService
) extends TraceLogger {
  import HandleUserNewReleaseNotificationDeliveryQueueFailureStep.OperationActionFailureTypes

  def run(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): PartialFunction[Throwable, Future[UserNewReleaseNotificationDeliveryQueueResult]] = {
    case failure: UserNewReleaseNotificationDeliveryQueueFailure =>
      run(target, failure, now)
    case NonFatal(exception) =>
      run(target, classifyFailure(target, exception), now)
  }

  def run(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      failure: UserNewReleaseNotificationDeliveryQueueFailure,
      now: BusinessDateTime
  )(using DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] = {
    val retryConfig = applicationConfig.spotify.client.retry

    if (
      requiresOperationAction(failure.failureType) ||
      (reachedMaxAttempts(target, retryConfig.maxAttempts) && !isRequestCapacityFailure(failure.failureType))
    ) {
      queueService
        .markBlocked(target, failure.failureType.dbValue, now)
        .map(queueUpdateResult(_, Blocked))
    } else {
      val nextAttemptAt = failure.nextAttemptAt.getOrElse {
        CalculateNextUserNewReleaseNotificationDeliveryQueueAttemptAt(
          now = now,
          failureCount = target.attemptCount,
          failure = failure,
          retryConfig = retryConfig
        )
      }
      queueService
        .markTemporaryFailure(target, failure.failureType.dbValue, nextAttemptAt, now)
        .map(queueUpdateResult(_, TemporaryFailure(failure.failureType, nextAttemptAt)))
    }
  }

  private def classifyFailure(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      exception: Throwable
  )(using LoggingContext): UserNewReleaseNotificationDeliveryQueueFailure = {
    val failure = UserNewReleaseNotificationDeliveryQueueFailureClassifier.fromThrowable(exception)
    if (failure.failureType == UserNewReleaseNotificationDeliveryQueueFailureType.Unknown) {
      warn(
        "ユーザー別新着リリース通知配信 target の想定外失敗を一時失敗として記録します",
        kv("userNewReleaseNotificationDeliveryQueueId", target.queueId),
        kv("userId", target.userId),
        kv("artistReleaseId", target.artistReleaseId),
        kv("failureClass", exception.getClass.getName)
      )
    }
    failure
  }

  private def reachedMaxAttempts(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      maxAttempts: Int
  ): Boolean =
    target.attemptCount >= maxAttempts

  private def requiresOperationAction(failureType: UserNewReleaseNotificationDeliveryQueueFailureType): Boolean =
    OperationActionFailureTypes.contains(failureType)

  private def isRequestCapacityFailure(
      failureType: UserNewReleaseNotificationDeliveryQueueFailureType
  ): Boolean =
    failureType == UserNewReleaseNotificationDeliveryQueueFailureType.RateLimited

  private def queueUpdateResult(
      result: UserNewReleaseNotificationDeliveryQueueUpdateResult,
      updatedResult: UserNewReleaseNotificationDeliveryQueueResult
  ): UserNewReleaseNotificationDeliveryQueueResult =
    result match {
      case UserNewReleaseNotificationDeliveryQueueUpdateResult.Updated => updatedResult
      case UserNewReleaseNotificationDeliveryQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
    }
}

private[user_new_release_notification_delivery_queue] object HandleUserNewReleaseNotificationDeliveryQueueFailureStep {
  private val OperationActionFailureTypes: Set[UserNewReleaseNotificationDeliveryQueueFailureType] =
    Set(
      UserNewReleaseNotificationDeliveryQueueFailureType.AuthorizationNotFound,
      UserNewReleaseNotificationDeliveryQueueFailureType.InsufficientScope,
      UserNewReleaseNotificationDeliveryQueueFailureType.PlaylistClientError,
      UserNewReleaseNotificationDeliveryQueueFailureType.ReleaseTracksNotFound,
      UserNewReleaseNotificationDeliveryQueueFailureType.InvalidGrant,
      UserNewReleaseNotificationDeliveryQueueFailureType.TokenDecryptFailed
    )
}
