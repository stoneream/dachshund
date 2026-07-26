package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context

private[user_new_release_notification_delivery] enum UserNewReleaseNotificationDeliveryResult {
  case Succeeded
  case Blocked
  case TemporaryFailure
  case StaleLockSkipped
}
