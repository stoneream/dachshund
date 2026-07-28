package io.github.stoneream.dachshund.config.spotify

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class SpotifyRequestPolicyConfig(
    pacingInterval: FiniteDuration,
    rateLimitFallbackDelay: FiniteDuration
)

object SpotifyRequestPolicyConfig {
  private final case class RawSpotifyRequestPolicyConfig(
      pacingInterval: FiniteDuration,
      rateLimitFallbackDelay: FiniteDuration
  ) derives ConfigReader

  given ConfigReader[SpotifyRequestPolicyConfig] =
    summon[ConfigReader[RawSpotifyRequestPolicyConfig]].emap(validate)

  private def validate(raw: RawSpotifyRequestPolicyConfig): Either[CannotConvert, SpotifyRequestPolicyConfig] =
    for {
      pacingInterval <- nonNegativeDuration("spotify.client.request-policy.pacing-interval", raw.pacingInterval)
      rateLimitFallbackDelay <- positiveDuration(
        "spotify.client.request-policy.rate-limit-fallback-delay",
        raw.rateLimitFallbackDelay
      )
    } yield SpotifyRequestPolicyConfig(
      pacingInterval = pacingInterval,
      rateLimitFallbackDelay = rateLimitFallbackDelay
    )

  private def nonNegativeDuration(
      fieldName: String,
      value: FiniteDuration
  ): Either[CannotConvert, FiniteDuration] =
    Either.cond(
      value.toNanos >= 0L,
      value,
      CannotConvert(value.toString, "FiniteDuration", s"$fieldName は 0 以上である必要があります")
    )

  private def positiveDuration(
      fieldName: String,
      value: FiniteDuration
  ): Either[CannotConvert, FiniteDuration] =
    Either.cond(
      value.length > 0L,
      value,
      CannotConvert(value.toString, "FiniteDuration", s"$fieldName は正数である必要があります")
    )
}
