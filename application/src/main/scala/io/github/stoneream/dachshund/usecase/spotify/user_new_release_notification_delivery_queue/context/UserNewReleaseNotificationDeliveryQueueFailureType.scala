package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context

private[user_new_release_notification_delivery_queue] enum UserNewReleaseNotificationDeliveryQueueFailureType(val dbValue: String) {
  case AuthorizationNotFound extends UserNewReleaseNotificationDeliveryQueueFailureType("authorization_not_found")
  case ConcurrentUpdate extends UserNewReleaseNotificationDeliveryQueueFailureType("access_token_concurrent_update")
  case InsufficientScope extends UserNewReleaseNotificationDeliveryQueueFailureType("insufficient_scope")
  case InvalidGrant extends UserNewReleaseNotificationDeliveryQueueFailureType("invalid_grant")
  case TokenDecryptFailed extends UserNewReleaseNotificationDeliveryQueueFailureType("token_decrypt_failed")
  case RateLimited extends UserNewReleaseNotificationDeliveryQueueFailureType("rate_limited")
  case Network extends UserNewReleaseNotificationDeliveryQueueFailureType("network")
  case ServerError extends UserNewReleaseNotificationDeliveryQueueFailureType("server_error")
  case InvalidResponse extends UserNewReleaseNotificationDeliveryQueueFailureType("invalid_response")
  case ClientError extends UserNewReleaseNotificationDeliveryQueueFailureType("client_error")
  case PlaylistClientError extends UserNewReleaseNotificationDeliveryQueueFailureType("playlist_client_error")
  case ReleaseTracksNotFound extends UserNewReleaseNotificationDeliveryQueueFailureType("release_tracks_not_found")
  case Unknown extends UserNewReleaseNotificationDeliveryQueueFailureType("unknown")
}

private[user_new_release_notification_delivery_queue] object UserNewReleaseNotificationDeliveryQueueFailureType {
  def fromDbValue(value: String): Option[UserNewReleaseNotificationDeliveryQueueFailureType] =
    values.find(_.dbValue == value)
}
