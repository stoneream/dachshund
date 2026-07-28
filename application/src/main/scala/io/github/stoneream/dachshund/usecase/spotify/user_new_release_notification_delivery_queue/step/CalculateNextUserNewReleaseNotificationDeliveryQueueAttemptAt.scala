package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.step

import io.github.stoneream.dachshund.config.retry.RetryConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.{UserNewReleaseNotificationDeliveryQueueFailure, UserNewReleaseNotificationDeliveryQueueFailureType}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

private[user_new_release_notification_delivery_queue] object CalculateNextUserNewReleaseNotificationDeliveryQueueAttemptAt {
  def apply(
      now: BusinessDateTime,
      failureCount: Int,
      failure: UserNewReleaseNotificationDeliveryQueueFailure,
      retryConfig: RetryConfig
  ): BusinessDateTime = {
    val delay =
      if (failure.failureType == UserNewReleaseNotificationDeliveryQueueFailureType.RateLimited) {
        failure.retryAfter.getOrElse(exponentialDelay(failureCount, retryConfig))
      } else {
        exponentialDelay(failureCount, retryConfig)
      }

    now.plus(ceilSeconds(delay).seconds)
  }

  private def exponentialDelay(failureCount: Int, retryConfig: RetryConfig): FiniteDuration = {
    val exponent = math.min(math.max(failureCount - 1, 0), 30)
    val multiplier = BigInt(1) << exponent
    val delayNanos =
      (BigInt(retryConfig.baseDelay.toNanos) * multiplier)
        .min(BigInt(retryConfig.maxDelay.toNanos))
        .toLong

    FiniteDuration(delayNanos, TimeUnit.NANOSECONDS)
  }

  private def ceilSeconds(duration: FiniteDuration): Long =
    math.max(1L, (duration.toMillis + 999L) / 1000L)
}
