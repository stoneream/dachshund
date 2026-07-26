package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context

import scala.concurrent.duration.FiniteDuration

private[user_new_release_notification_delivery] final case class UserNewReleaseNotificationDeliveryFailure(
    failureType: String,
    retryAfter: Option[FiniteDuration] = None
) extends Exception(s"user new release notification delivery failed: $failureType")
