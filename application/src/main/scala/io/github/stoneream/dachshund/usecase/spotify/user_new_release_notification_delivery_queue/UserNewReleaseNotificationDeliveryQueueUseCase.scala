package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.ReleaseNotificationType
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.model.UserNewReleaseNotificationDeliveryQueueTarget
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.{UserNewReleaseNotificationDeliveryQueueService, UserNewReleaseNotificationDeliveryQueueServiceException as QueueServiceException}
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.{UserNewReleaseNotificationDeliveryQueueFailure, UserNewReleaseNotificationDeliveryQueueFailureType, UserNewReleaseNotificationDeliveryQueueResult}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.UserNewReleaseNotificationDeliveryQueueResult.TemporaryFailure
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.step.{DeliverUserNewReleaseNotificationTargetStep, FindReleaseTrackUrisStep, HandleUserNewReleaseNotificationDeliveryQueueFailureStep, ReleaseUserNewReleaseNotificationDeliveryQueueTargetsStep}

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class UserNewReleaseNotificationDeliveryQueueUseCase @Inject() (
    queueService: UserNewReleaseNotificationDeliveryQueueService,
    releaseTargetsStep: ReleaseUserNewReleaseNotificationDeliveryQueueTargetsStep,
    findReleaseTrackUrisStep: FindReleaseTrackUrisStep,
    deliverTargetStep: DeliverUserNewReleaseNotificationTargetStep,
    handleFailureStep: HandleUserNewReleaseNotificationDeliveryQueueFailureStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UserNewReleaseNotificationDeliveryQueueUseCaseInput,
      UserNewReleaseNotificationDeliveryQueueUseCaseOutput,
      UserNewReleaseNotificationDeliveryQueueUseCaseException
    ]
    with TraceLogger {

  override def run(
      input: UserNewReleaseNotificationDeliveryQueueUseCaseInput
  )(using LoggingContext): Future[UserNewReleaseNotificationDeliveryQueueUseCaseOutput] = {
    given DefaultExecutor = defaultExecutor
    (for {
      targets <- claimTargets(input)
      _ <- syncClaimedTargets(targets, input)
      output <- completeUseCase()
    } yield output).recoverWith {
      case QueueServiceException.TargetClaimFailed(queueId) =>
        Future.failed(UserNewReleaseNotificationDeliveryQueueUseCaseException.TargetClaimFailed(queueId))
      case useCaseException: UserNewReleaseNotificationDeliveryQueueUseCaseException =>
        Future.failed(useCaseException)
    }
  }

  private def claimTargets(
      input: UserNewReleaseNotificationDeliveryQueueUseCaseInput
  ): Future[Seq[UserNewReleaseNotificationDeliveryQueueTarget]] =
    queueService.claimDueTargets(
      now = input.now,
      releaseNotificationType = ReleaseNotificationType.Playlist,
      batchSize = input.batchSize,
      processingLease = input.processingLease
    )

  private def syncClaimedTargets(
      targets: Seq[UserNewReleaseNotificationDeliveryQueueTarget],
      input: UserNewReleaseNotificationDeliveryQueueUseCaseInput
  )(using LoggingContext, DefaultExecutor): Future[Seq[UserNewReleaseNotificationDeliveryQueueResult]] =
    (for {
      results <- syncClaimedTargetSteps(targets, input)
    } yield results).recoverWith { case NonFatal(exception) =>
      releaseTargetsAndFail(targets, input.now, exception)
    }

  private def syncClaimedTargetSteps(
      targets: Seq[UserNewReleaseNotificationDeliveryQueueTarget],
      input: UserNewReleaseNotificationDeliveryQueueUseCaseInput
  )(using LoggingContext, DefaultExecutor): Future[Seq[UserNewReleaseNotificationDeliveryQueueResult]] =
    for {
      _ <- logClaimedTargets(input.batchSize, targets.size)
      results <- syncTargets(targets, input.now)
    } yield results

  private def syncTargets(
      targets: Seq[UserNewReleaseNotificationDeliveryQueueTarget],
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[Seq[UserNewReleaseNotificationDeliveryQueueResult]] =
    syncNext(targets.toList, targets.size, now, Vector.empty)

  private def syncNext(
      remainingTargets: List[UserNewReleaseNotificationDeliveryQueueTarget],
      selectedCount: Int,
      now: BusinessDateTime,
      results: Vector[UserNewReleaseNotificationDeliveryQueueResult]
  )(using LoggingContext, DefaultExecutor): Future[Seq[UserNewReleaseNotificationDeliveryQueueResult]] =
    remainingTargets match {
      case Nil =>
        Future.successful(results)
      case target :: tail =>
        syncTarget(target, now).flatMap { result =>
          logProgress(target, result, selectedCount).flatMap { loggedResult =>
            result match {
              case TemporaryFailure(failureType, nextAttemptAt) if shouldStopBatch(failureType) =>
                deferRemainingTargets(tail, failureType, nextAttemptAt, now).map(_ => results :+ loggedResult)
              case _ =>
                syncNext(tail, selectedCount, now, results :+ loggedResult)
            }
          }
        }
    }

  private def deferRemainingTargets(
      targets: List[UserNewReleaseNotificationDeliveryQueueTarget],
      failureType: UserNewReleaseNotificationDeliveryQueueFailureType,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[Unit] = {
    warn(
      "Spotify のリクエスト制限によりユーザー別新着リリース通知配信バッチの残りを延期します",
      kv("userNewReleaseNotificationDeliveryQueue.failureType", failureType.dbValue),
      kv("userNewReleaseNotificationDeliveryQueue.deferredCount", targets.size),
      kv("userNewReleaseNotificationDeliveryQueue.nextAttemptAt", nextAttemptAt)
    )
    targets.foldLeft(Future.successful(())) { (futureDone, target) =>
      futureDone.flatMap(_ => queueService.markTemporaryFailure(target, failureType.dbValue, nextAttemptAt, now).map(_ => ()))
    }
  }

  private def shouldStopBatch(failureType: UserNewReleaseNotificationDeliveryQueueFailureType): Boolean =
    failureType == UserNewReleaseNotificationDeliveryQueueFailureType.RateLimited

  private def syncTarget(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] =
    target.releaseNotificationType match {
      case ReleaseNotificationType.Playlist =>
        syncPlaylistTarget(target, now)
    }

  private def syncPlaylistTarget(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] =
    (for {
      targetTrackUris <- loadTargetTrackUris(target)
      result <- deliverTargetStep.run(target, targetTrackUris, now)
    } yield result).recoverWith(handleFailureStep.run(target, now))

  private def loadTargetTrackUris(
      target: UserNewReleaseNotificationDeliveryQueueTarget
  )(using DefaultExecutor): Future[Seq[String]] =
    for {
      releaseTrackUris <- findReleaseTrackUrisStep.run(target.artistReleaseId)
      targetTrackUris <- normalizeTargetTrackUris(releaseTrackUris)
    } yield targetTrackUris

  private def normalizeTargetTrackUris(
      releaseTrackUris: Seq[String]
  ): Future[Seq[String]] = {
    val targetTrackUris = releaseTrackUris.map(_.trim).filter(_.nonEmpty).distinct
    if (targetTrackUris.isEmpty) {
      Future.failed[Seq[String]](UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.ReleaseTracksNotFound))
    } else {
      Future.successful(targetTrackUris)
    }
  }

  private def logClaimedTargets(
      batchSize: Int,
      selectedCount: Int
  )(using LoggingContext): Future[Unit] = {
    info(
      "ユーザー別新着リリース通知配信の対象を取得しました",
      kv("userNewReleaseNotificationDeliveryQueue.batchSize", batchSize),
      kv("userNewReleaseNotificationDeliveryQueue.selectedCount", selectedCount)
    )
    Future.successful(())
  }

  private def logProgress(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      result: UserNewReleaseNotificationDeliveryQueueResult,
      selectedCount: Int
  )(using LoggingContext): Future[UserNewReleaseNotificationDeliveryQueueResult] = {
    info(
      "ユーザー別新着リリース通知配信を処理中です",
      kv("userNewReleaseNotificationDeliveryQueueId", target.queueId),
      kv("userId", target.userId),
      kv("artistReleaseId", target.artistReleaseId),
      kv("userNewReleaseNotificationDeliveryQueue.result", result),
      kv("userNewReleaseNotificationDeliveryQueue.selectedCount", selectedCount)
    )
    Future.successful(result)
  }

  private def completeUseCase(): Future[UserNewReleaseNotificationDeliveryQueueUseCaseOutput] =
    Future.successful(UserNewReleaseNotificationDeliveryQueueUseCaseOutput())

  private def releaseTargetsAndFail(
      targets: Seq[UserNewReleaseNotificationDeliveryQueueTarget],
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[Seq[UserNewReleaseNotificationDeliveryQueueResult]] =
    for {
      _ <- releaseTargetsStep.run(targets, now, exception)
      results <- Future.failed[Seq[UserNewReleaseNotificationDeliveryQueueResult]](exception)
    } yield results
}
