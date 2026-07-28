package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

import scala.concurrent.duration.FiniteDuration

private[user_new_release_notification_delivery_queue] final case class UserNewReleaseNotificationDeliveryQueueFailure(
    failureType: UserNewReleaseNotificationDeliveryQueueFailureType,
    retryAfter: Option[FiniteDuration] = None,
    nextAttemptAt: Option[BusinessDateTime] = None
) extends Exception(s"user new release notification delivery queue failed: ${failureType.dbValue}")
