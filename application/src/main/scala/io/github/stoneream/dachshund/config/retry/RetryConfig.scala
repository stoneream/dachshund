package io.github.stoneream.dachshund.config.retry

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class RetryConfig(
    maxAttempts: Int,
    baseDelay: FiniteDuration,
    maxDelay: FiniteDuration,
    jitterRatio: Option[Double],
    rateLimitMaxDelay: Option[FiniteDuration]
) derives ConfigReader
