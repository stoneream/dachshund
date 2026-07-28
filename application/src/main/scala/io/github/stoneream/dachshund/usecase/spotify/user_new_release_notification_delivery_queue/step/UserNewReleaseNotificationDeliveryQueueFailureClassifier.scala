package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.step

import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as TokenProviderException
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.{UserNewReleaseNotificationDeliveryQueueFailure, UserNewReleaseNotificationDeliveryQueueFailureType}

private[user_new_release_notification_delivery_queue] object UserNewReleaseNotificationDeliveryQueueFailureClassifier {
  def fromThrowable(exception: Throwable): UserNewReleaseNotificationDeliveryQueueFailure =
    exception match {
      case failure: UserNewReleaseNotificationDeliveryQueueFailure =>
        failure
      case TokenProviderException.AuthorizationNotFound(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.AuthorizationNotFound)
      case TokenProviderException.ReauthorizationRequired(_, reasonType, _) =>
        UserNewReleaseNotificationDeliveryQueueFailure(failureTypeFromString(reasonType))
      case TokenProviderException.TemporaryFailure(_, failureType, nextAttemptAt, _) =>
        UserNewReleaseNotificationDeliveryQueueFailure(failureTypeFromString(failureType), nextAttemptAt = Some(nextAttemptAt))
      case TokenProviderException.ConcurrentUpdate(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.ConcurrentUpdate)
      case TokenProviderException.Unknown(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.Unknown)
      case ClientException.Unauthorized(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.ClientError)
      case ClientException.Forbidden(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.InsufficientScope)
      case ClientException.RateLimited(retryAfter, _) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.RateLimited, retryAfter)
      case ClientException.Network(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.Network)
      case ClientException.ServerError(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.ServerError)
      case ClientException.InvalidResponse(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.InvalidResponse)
      case ClientException.ClientError(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.PlaylistClientError)
      case ClientException.Unknown(_) =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.Unknown)
      case _ =>
        UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.Unknown)
    }

  private def failureTypeFromString(value: String): UserNewReleaseNotificationDeliveryQueueFailureType =
    UserNewReleaseNotificationDeliveryQueueFailureType
      .fromDbValue(value)
      .getOrElse(UserNewReleaseNotificationDeliveryQueueFailureType.Unknown)
}
