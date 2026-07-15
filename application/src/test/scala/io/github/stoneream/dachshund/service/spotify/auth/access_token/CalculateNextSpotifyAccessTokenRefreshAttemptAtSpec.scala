package io.github.stoneream.dachshund.service.spotify.auth.access_token

import io.github.stoneream.dachshund.config.retry.RetryConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.auth.access_token.context.{SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshFailureReason}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.step.CalculateNextSpotifyAccessTokenRefreshAttemptAt
import org.scalatest.featurespec.AnyFeatureSpec

import scala.concurrent.duration.*

class CalculateNextSpotifyAccessTokenRefreshAttemptAtSpec extends AnyFeatureSpec {
  Feature("Spotify access token refresh retry schedule") {
    Scenario("rate limit retry-after を上限で丸め、通常失敗は指数 backoff にする") {
      val retryConfig = RetryConfig(
        maxAttempts = 3,
        baseDelay = 1.second,
        maxDelay = 30.seconds,
        jitterRatio = None,
        rateLimitMaxDelay = Some(10.seconds)
      )

      val rateLimited = CalculateNextSpotifyAccessTokenRefreshAttemptAt(
        now = fixedNow,
        failureCount = 1,
        failure = SpotifyAccessTokenRefreshFailure(
          reason = SpotifyAccessTokenRefreshFailureReason.RateLimited,
          retryAfter = Some(15.seconds)
        ),
        retryConfig = retryConfig
      )
      val network = CalculateNextSpotifyAccessTokenRefreshAttemptAt(
        now = fixedNow,
        failureCount = 3,
        failure = SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.Network),
        retryConfig = retryConfig
      )

      assert(rateLimited.toLocalDateTime == fixedNow.plus(10.seconds).toLocalDateTime)
      assert(network.toLocalDateTime == fixedNow.plus(4.seconds).toLocalDateTime)
    }

    Scenario("1 秒未満の delay は 1 秒へ切り上げる") {
      val retryConfig = RetryConfig(
        maxAttempts = 3,
        baseDelay = 1.millis,
        maxDelay = 30.seconds,
        jitterRatio = None,
        rateLimitMaxDelay = None
      )

      val result = CalculateNextSpotifyAccessTokenRefreshAttemptAt(
        now = fixedNow,
        failureCount = 1,
        failure = SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.Network),
        retryConfig = retryConfig
      )

      assert(result.toLocalDateTime == fixedNow.plus(1.second).toLocalDateTime)
    }
  }

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")
}
