package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.step

import io.github.stoneream.dachshund.config.retry.RetryConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.{FollowedArtistsSyncFailure, FollowedArtistsSyncFailureType}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

private[followed_artists_sync] object CalculateNextFollowedArtistsSyncAttemptAt {
  def apply(
      now: BusinessDateTime,
      failureCount: Int,
      failure: FollowedArtistsSyncFailure,
      retryConfig: RetryConfig
  ): BusinessDateTime = {
    val delay =
      if (failure.failureType == FollowedArtistsSyncFailureType.RateLimited) {
        failure.retryAfter.map(capRateLimitDelay(_, retryConfig)).getOrElse(exponentialDelay(failureCount, retryConfig))
      } else {
        exponentialDelay(failureCount, retryConfig)
      }

    now.plus(ceilSeconds(delay).seconds)
  }

  private def capRateLimitDelay(delay: FiniteDuration, retryConfig: RetryConfig): FiniteDuration =
    retryConfig.rateLimitMaxDelay
      .map(maxDelay => if (delay > maxDelay) maxDelay else delay)
      .getOrElse(delay)

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
