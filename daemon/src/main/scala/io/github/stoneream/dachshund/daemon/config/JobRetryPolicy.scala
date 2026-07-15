package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class JobRetryPolicy(
    maxAttempts: Int,
    baseDelay: FiniteDuration,
    maxDelay: FiniteDuration,
    jitterRatio: Option[Double]
) {
  val effectiveMaxDelay: FiniteDuration =
    if (maxDelay < baseDelay) baseDelay else maxDelay
}

object JobRetryPolicy {
  private final case class RawJobRetryPolicy(
      maxAttempts: Int,
      baseDelay: FiniteDuration,
      maxDelay: FiniteDuration,
      jitterRatio: Option[Double]
  ) derives ConfigReader

  given ConfigReader[JobRetryPolicy] =
    summon[ConfigReader[RawJobRetryPolicy]].emap(validate)

  private def validate(raw: RawJobRetryPolicy): Either[CannotConvert, JobRetryPolicy] =
    for {
      maxAttempts <- DaemonConfigValidation.positiveInt("retry.max-attempts", raw.maxAttempts)
      baseDelay <- DaemonConfigValidation.nonNegativeDuration("retry.base-delay", raw.baseDelay)
      maxDelay <- DaemonConfigValidation.nonNegativeDuration("retry.max-delay", raw.maxDelay)
      jitterRatio <- DaemonConfigValidation.nonNegativeDoubleOption("retry.jitter-ratio", raw.jitterRatio)
    } yield JobRetryPolicy(
      maxAttempts = maxAttempts,
      baseDelay = baseDelay,
      maxDelay = maxDelay,
      jitterRatio = jitterRatio
    )
}
