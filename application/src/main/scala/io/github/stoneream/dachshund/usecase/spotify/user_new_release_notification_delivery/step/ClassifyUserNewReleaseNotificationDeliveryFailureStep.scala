package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as TokenProviderException
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context.{UserNewReleaseNotificationDeliveryFailure, UserNewReleaseNotificationDeliveryFailureType}

@Singleton
private[user_new_release_notification_delivery] class ClassifyUserNewReleaseNotificationDeliveryFailureStep @Inject() () {
  def run(exception: Throwable): UserNewReleaseNotificationDeliveryFailure =
    exception match {
      case failure: UserNewReleaseNotificationDeliveryFailure =>
        failure
      case TokenProviderException.AuthorizationNotFound(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.AuthorizationNotFound)
      case TokenProviderException.ReauthorizationRequired(_, reasonType, _) =>
        UserNewReleaseNotificationDeliveryFailure(reasonType)
      case TokenProviderException.TemporaryFailure(_, failureType, _, _) =>
        UserNewReleaseNotificationDeliveryFailure(failureType)
      case TokenProviderException.ConcurrentUpdate(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.ConcurrentUpdate)
      case ClientException.Unauthorized(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.ClientError)
      case ClientException.Forbidden(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.InsufficientScope)
      case ClientException.RateLimited(retryAfter, _) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.RateLimited, retryAfter)
      case ClientException.Network(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.Network)
      case ClientException.ServerError(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.ServerError)
      case ClientException.InvalidResponse(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.InvalidResponse)
      case ClientException.ClientError(_) =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.PlaylistClientError)
      case _ =>
        UserNewReleaseNotificationDeliveryFailure(UserNewReleaseNotificationDeliveryFailureType.Unknown)
    }
}
