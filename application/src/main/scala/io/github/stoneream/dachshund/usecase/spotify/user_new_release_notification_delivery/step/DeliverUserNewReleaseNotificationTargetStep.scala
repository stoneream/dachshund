package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model.UserNewReleaseNotificationQueueTarget
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.{UserNewReleaseNotificationQueueService, UserNewReleaseNotificationQueueUpdateResult}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException as ClientException}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context.UserNewReleaseNotificationDeliveryResult.{StaleLockSkipped, Succeeded}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context.{UserNewReleaseNotificationDeliveryFailure, UserNewReleaseNotificationDeliveryFailureType, UserNewReleaseNotificationDeliveryResult}

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
private[user_new_release_notification_delivery] class DeliverUserNewReleaseNotificationTargetStep @Inject() (
    queueService: UserNewReleaseNotificationQueueService,
    authorizationCodeAccessTokenProvider: SpotifyAuthorizationCodeAccessTokenProvider,
    spotifyClient: SpotifyClient,
    findReleaseTrackUrisStep: FindReleaseTrackUrisStep,
    classifyFailureStep: ClassifyUserNewReleaseNotificationDeliveryFailureStep
) extends TraceLogger {
  def run(
      target: UserNewReleaseNotificationQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    deliver(target, now, forceRefresh = false, retryUnauthorized = true)
      .recoverWith(recoverUnexpectedTargetFailure(target))

  private def deliver(
      target: UserNewReleaseNotificationQueueTarget,
      now: BusinessDateTime,
      forceRefresh: Boolean,
      retryUnauthorized: Boolean
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    loadTargetTrackUris(target).flatMap { targetTrackUris =>
      deliverLoadedTrackUris(
        target = target,
        targetTrackUris = targetTrackUris,
        now = now,
        forceRefresh = forceRefresh,
        retryUnauthorized = retryUnauthorized
      )
    }

  private def loadTargetTrackUris(
      target: UserNewReleaseNotificationQueueTarget
  )(using DefaultExecutor): Future[Seq[String]] =
    findReleaseTrackUrisStep
      .run(target.artistReleaseId)
      .flatMap { releaseTrackUris =>
        val targetTrackUris = releaseTrackUris.map(_.trim).filter(_.nonEmpty).distinct
        if (targetTrackUris.isEmpty) {
          Future.failed[Seq[String]](UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.ReleaseTracksNotFound))
        } else {
          Future.successful(targetTrackUris)
        }
      }

  private def deliverLoadedTrackUris(
      target: UserNewReleaseNotificationQueueTarget,
      targetTrackUris: Seq[String],
      now: BusinessDateTime,
      forceRefresh: Boolean,
      retryUnauthorized: Boolean
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    (for {
      accessToken <- resolveAccessToken(target, now, forceRefresh)
      result <- appendTargetTrackUris(target, accessToken.accessToken, targetTrackUris, now)
    } yield {
      logDelivered(target, targetTrackUris.size)
      result
    }).recoverWith(recoverDeliveryFailure(target, now, retryUnauthorized))

  private def resolveAccessToken(
      target: UserNewReleaseNotificationQueueTarget,
      now: BusinessDateTime,
      forceRefresh: Boolean
  )(using LoggingContext): Future[SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken] =
    authorizationCodeAccessTokenProvider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(target.userId, now, forceRefresh = forceRefresh))

  private def appendTargetTrackUris(
      target: UserNewReleaseNotificationQueueTarget,
      accessToken: String,
      targetTrackUris: Seq[String],
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    spotifyClient
      .addItemsToPlaylist(
        accessToken = accessToken,
        spotifyPlaylistCode = target.spotifyPlaylistCode,
        trackUris = targetTrackUris
      )
      .flatMap(addResult => completeDelivery(target, addResult.spotifySnapshotId, now))

  private def completeDelivery(
      target: UserNewReleaseNotificationQueueTarget,
      spotifySnapshotId: String,
      now: BusinessDateTime
  )(using DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    queueService
      .markSucceeded(target, spotifySnapshotId, now)
      .map {
        case UserNewReleaseNotificationQueueUpdateResult.Updated => Succeeded
        case UserNewReleaseNotificationQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
      }

  private def recoverDeliveryFailure(
      target: UserNewReleaseNotificationQueueTarget,
      now: BusinessDateTime,
      retryUnauthorized: Boolean
  )(using LoggingContext, DefaultExecutor): PartialFunction[Throwable, Future[UserNewReleaseNotificationDeliveryResult]] = {
    case ClientException.Unauthorized(_) if retryUnauthorized =>
      retryWithRefreshedAccessToken(target, now)
    case NonFatal(exception) =>
      Future.failed(classifyFailureStep.run(exception))
  }

  private def retryWithRefreshedAccessToken(
      target: UserNewReleaseNotificationQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryResult] =
    deliver(target, now, forceRefresh = true, retryUnauthorized = false)

  private def recoverUnexpectedTargetFailure(
      target: UserNewReleaseNotificationQueueTarget
  )(using LoggingContext): PartialFunction[Throwable, Future[UserNewReleaseNotificationDeliveryResult]] = {
    case failure: UserNewReleaseNotificationDeliveryFailure =>
      Future.failed(failure)
    case NonFatal(exception) =>
      handleUnexpectedTargetFailure(target, exception)
  }

  private def handleUnexpectedTargetFailure(
      target: UserNewReleaseNotificationQueueTarget,
      exception: Throwable
  )(using LoggingContext): Future[Nothing] = {
    warn(
      "ユーザー別新着リリース通知配信 target の想定外失敗を一時失敗として記録します",
      kv("userNewReleaseNotificationQueueId", target.queueId),
      kv("userId", target.userId),
      kv("artistReleaseId", target.artistReleaseId),
      kv("failureClass", exception.getClass.getName)
    )
    Future.failed(UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.Unknown))
  }

  private def logDelivered(
      target: UserNewReleaseNotificationQueueTarget,
      requestedTrackCount: Int
  )(using LoggingContext): Unit =
    info(
      "ユーザー別新着リリース通知を配信しました",
      kv("userNewReleaseNotificationQueueId", target.queueId),
      kv("userId", target.userId),
      kv("artistReleaseId", target.artistReleaseId),
      kv("userNewReleaseNotificationDelivery.requestedTrackCount", requestedTrackCount)
    )
}
