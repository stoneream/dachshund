package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.model.UserNewReleaseNotificationDeliveryQueueTarget
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.{UserNewReleaseNotificationDeliveryQueueService, UserNewReleaseNotificationDeliveryQueueUpdateResult}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException as ClientException}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.UserNewReleaseNotificationDeliveryQueueResult.{StaleLockSkipped, Succeeded}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.UserNewReleaseNotificationDeliveryQueueResult

import scala.concurrent.Future

@Singleton
private[user_new_release_notification_delivery_queue] class DeliverUserNewReleaseNotificationTargetStep @Inject() (
    queueService: UserNewReleaseNotificationDeliveryQueueService,
    authorizationCodeAccessTokenProvider: SpotifyAuthorizationCodeAccessTokenProvider,
    spotifyClient: SpotifyClient
) extends TraceLogger {
  def run(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      targetTrackUris: Seq[String],
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] =
    deliver(
      target = target,
      targetTrackUris = targetTrackUris,
      now = now,
      forceRefresh = false,
      retryUnauthorized = true
    )

  private def deliver(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      targetTrackUris: Seq[String],
      now: BusinessDateTime,
      forceRefresh: Boolean,
      retryUnauthorized: Boolean
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] =
    (for {
      accessToken <- resolveAccessToken(target, now, forceRefresh)
      result <- appendTargetTrackUris(target, accessToken.accessToken, targetTrackUris, now)
    } yield {
      logDelivered(target, targetTrackUris.size)
      result
    }).recoverWith(recoverUnauthorizedDeliveryFailure(target, targetTrackUris, now, retryUnauthorized))

  private def resolveAccessToken(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      now: BusinessDateTime,
      forceRefresh: Boolean
  )(using LoggingContext): Future[SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken] =
    authorizationCodeAccessTokenProvider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(target.userId, now, forceRefresh = forceRefresh))

  private def appendTargetTrackUris(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      accessToken: String,
      targetTrackUris: Seq[String],
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] =
    spotifyClient
      .addItemsToPlaylist(
        accessToken = accessToken,
        spotifyPlaylistCode = target.spotifyPlaylistCode,
        trackUris = targetTrackUris
      )
      .flatMap(addResult => completeDelivery(target, addResult.spotifySnapshotId, now))

  private def completeDelivery(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      spotifySnapshotId: String,
      now: BusinessDateTime
  )(using DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] =
    queueService
      .markSucceeded(target, spotifySnapshotId, now)
      .map {
        case UserNewReleaseNotificationDeliveryQueueUpdateResult.Updated => Succeeded
        case UserNewReleaseNotificationDeliveryQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
      }

  private def recoverUnauthorizedDeliveryFailure(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      targetTrackUris: Seq[String],
      now: BusinessDateTime,
      retryUnauthorized: Boolean
  )(using LoggingContext, DefaultExecutor): PartialFunction[Throwable, Future[UserNewReleaseNotificationDeliveryQueueResult]] = {
    case ClientException.Unauthorized(_) if retryUnauthorized =>
      retryWithRefreshedAccessToken(target, targetTrackUris, now)
  }

  private def retryWithRefreshedAccessToken(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      targetTrackUris: Seq[String],
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[UserNewReleaseNotificationDeliveryQueueResult] =
    deliver(target, targetTrackUris, now, forceRefresh = true, retryUnauthorized = false)

  private def logDelivered(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      requestedTrackCount: Int
  )(using LoggingContext): Unit =
    info(
      "ユーザー別新着リリース通知を配信しました",
      kv("userNewReleaseNotificationDeliveryQueueId", target.queueId),
      kv("userId", target.userId),
      kv("artistReleaseId", target.artistReleaseId),
      kv("userNewReleaseNotificationDeliveryQueue.requestedTrackCount", requestedTrackCount)
    )
}
