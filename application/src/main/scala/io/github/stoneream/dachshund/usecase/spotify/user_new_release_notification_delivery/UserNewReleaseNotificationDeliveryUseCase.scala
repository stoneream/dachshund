package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.ReleaseNotificationType
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.{UserNewReleaseNotificationQueueService, UserNewReleaseNotificationQueueServiceException as QueueServiceException}
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model.UserNewReleaseNotificationQueueTarget
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context.{UserNewReleaseNotificationDeliveryFailure, UserNewReleaseNotificationDeliveryResult}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.step.{DeliverUserNewReleaseNotificationTargetStep, HandleUserNewReleaseNotificationDeliveryFailureStep, ReleaseUserNewReleaseNotificationDeliveryTargetsStep, SyncUserNewReleaseNotificationDeliveryTargetsStep}

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class UserNewReleaseNotificationDeliveryUseCase @Inject() (
    queueService: UserNewReleaseNotificationQueueService,
    deliverTargetStep: DeliverUserNewReleaseNotificationTargetStep,
    handleFailureStep: HandleUserNewReleaseNotificationDeliveryFailureStep,
    releaseTargetsStep: ReleaseUserNewReleaseNotificationDeliveryTargetsStep,
    syncTargetsStep: SyncUserNewReleaseNotificationDeliveryTargetsStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UserNewReleaseNotificationDeliveryUseCaseInput,
      UserNewReleaseNotificationDeliveryUseCaseOutput,
      UserNewReleaseNotificationDeliveryUseCaseException
    ]
    with TraceLogger {

  override def run(
      input: UserNewReleaseNotificationDeliveryUseCaseInput
  )(using LoggingContext): Future[UserNewReleaseNotificationDeliveryUseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    claimAndSyncTargets(input).recoverWith { case NonFatal(exception) =>
      Future.failed(toUseCaseException(exception))
    }
  }

  private def claimAndSyncTargets(
      input: UserNewReleaseNotificationDeliveryUseCaseInput
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryUseCaseOutput] =
    queueService
      .claimDueTargets(
        now = input.now,
        releaseNotificationType = ReleaseNotificationType.Playlist,
        batchSize = input.batchSize,
        processingLease = input.processingLease
      )
      .flatMap { targets =>
        syncClaimedTargets(targets, input.now, input.batchSize)
      }

  private def syncClaimedTargets(
      targets: Seq[UserNewReleaseNotificationQueueTarget],
      now: BusinessDateTime,
      batchSize: Int
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryUseCaseOutput] = {
    logTargetsSelected(batchSize, targets.size)
    syncTargetsStep
      .run(targets)(target => syncTarget(target, now))
      .map(_ => UserNewReleaseNotificationDeliveryUseCaseOutput())
      .recoverWith { case NonFatal(exception) =>
        releaseTargetsAndFail(targets, now, exception)
      }
  }

  private def syncTarget(
      target: UserNewReleaseNotificationQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    deliverTargetStep.run(target, now).recoverWith { case failure: UserNewReleaseNotificationDeliveryFailure =>
      handleFailureStep.run(target, failure, now)
    }

  private def releaseTargetsAndFail(
      targets: Seq[UserNewReleaseNotificationQueueTarget],
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryUseCaseOutput] =
    releaseTargetsStep
      .run(targets, now, exception)
      .flatMap(_ => Future.failed[UserNewReleaseNotificationDeliveryUseCaseOutput](exception))

  private def toUseCaseException(exception: Throwable): UserNewReleaseNotificationDeliveryUseCaseException =
    exception match {
      case QueueServiceException.TargetClaimFailed(queueId) =>
        UserNewReleaseNotificationDeliveryUseCaseException.TargetClaimFailed(queueId)
      case useCaseException: UserNewReleaseNotificationDeliveryUseCaseException =>
        useCaseException
      case _ =>
        UserNewReleaseNotificationDeliveryUseCaseException.Unexpected(exception)
    }

  private def logTargetsSelected(
      batchSize: Int,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "ユーザー別新着リリース通知配信の対象を取得しました",
      kv("userNewReleaseNotificationDelivery.batchSize", batchSize),
      kv("userNewReleaseNotificationDelivery.selectedCount", selectedCount)
    )

}
