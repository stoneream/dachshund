package io.github.stoneream.dachshund.service.spotify.auth.access_token.step

import io.github.stoneream.dachshund.config.retry.RetryConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.auth.access_token.context.{SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshFailureReason}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

/** Spotify アクセストークン更新の次回リトライ時刻を計算する。 */
private[auth] object CalculateNextSpotifyAccessTokenRefreshAttemptAt {
  def apply(
      now: BusinessDateTime,
      failureCount: Int,
      failure: SpotifyAccessTokenRefreshFailure,
      retryConfig: RetryConfig
  ): BusinessDateTime = {
    val delay =
      if (failure.reason == SpotifyAccessTokenRefreshFailureReason.RateLimited) {
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
