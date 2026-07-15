package io.github.stoneream.dachshund.config.spotify

import io.github.stoneream.dachshund.config.retry.RetryConfig
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class SpotifyClientConfig(
    apiBaseUrl: String,
    accountsBaseUrl: String,
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    connectTimeout: FiniteDuration,
    requestTimeout: FiniteDuration,
    retry: RetryConfig
)

object SpotifyClientConfig {
  private final case class RawSpotifyClientConfig(
      apiBaseUrl: String,
      accountsBaseUrl: String,
      clientId: String,
      clientSecret: String,
      redirectUri: String,
      connectTimeout: FiniteDuration,
      requestTimeout: FiniteDuration,
      retry: RetryConfig
  ) derives ConfigReader

  given ConfigReader[SpotifyClientConfig] =
    summon[ConfigReader[RawSpotifyClientConfig]].emap(validate)

  private def validate(raw: RawSpotifyClientConfig): Either[CannotConvert, SpotifyClientConfig] =
    for {
      clientId <- SpotifyConfigValidation.requireTrimmed("spotify.client.client-id", raw.clientId)
      clientSecret <- SpotifyConfigValidation.requireTrimmed("spotify.client.client-secret", raw.clientSecret)
      redirectUri <- SpotifyConfigValidation.requireTrimmed("spotify.client.redirect-uri", raw.redirectUri)
    } yield SpotifyClientConfig(
      apiBaseUrl = raw.apiBaseUrl,
      accountsBaseUrl = raw.accountsBaseUrl,
      clientId = clientId,
      clientSecret = clientSecret,
      redirectUri = redirectUri,
      connectTimeout = raw.connectTimeout,
      requestTimeout = raw.requestTimeout,
      retry = raw.retry
    )
}
