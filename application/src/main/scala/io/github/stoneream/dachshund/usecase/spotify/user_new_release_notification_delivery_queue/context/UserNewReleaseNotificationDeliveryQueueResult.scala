package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context

private[user_new_release_notification_delivery_queue] enum UserNewReleaseNotificationDeliveryQueueResult {
  case Succeeded
  case Blocked
  case TemporaryFailure
  case StaleLockSkipped
}
