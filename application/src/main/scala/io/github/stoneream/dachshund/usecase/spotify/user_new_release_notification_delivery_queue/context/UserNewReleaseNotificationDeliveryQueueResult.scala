package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

private[user_new_release_notification_delivery_queue] enum UserNewReleaseNotificationDeliveryQueueResult {
  case Succeeded
  case Blocked
  case TemporaryFailure(
      failureType: UserNewReleaseNotificationDeliveryQueueFailureType,
      nextAttemptAt: BusinessDateTime
  )
  case StaleLockSkipped
}
