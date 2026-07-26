package io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model

final case class UserNewReleaseNotificationQueueClaimResult(
    target: UserNewReleaseNotificationQueueTarget,
    claimed: Boolean
)
