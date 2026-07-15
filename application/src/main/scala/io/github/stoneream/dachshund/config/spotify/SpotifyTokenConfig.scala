package io.github.stoneream.dachshund.config.spotify

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class SpotifyTokenConfig(
    refreshMargin: FiniteDuration,
    encryptionKey: String,
    encryptionKeyVersion: String
)

object SpotifyTokenConfig {
  private final case class RawSpotifyTokenConfig(
      refreshMargin: FiniteDuration,
      encryptionKey: String,
      encryptionKeyVersion: String
  ) derives ConfigReader

  given ConfigReader[SpotifyTokenConfig] =
    summon[ConfigReader[RawSpotifyTokenConfig]].emap(validate)

  private def validate(raw: RawSpotifyTokenConfig): Either[CannotConvert, SpotifyTokenConfig] =
    for {
      encryptionKey <- SpotifyConfigValidation.requireTrimmed("spotify.token.encryption-key", raw.encryptionKey)
    } yield SpotifyTokenConfig(
      refreshMargin = raw.refreshMargin,
      encryptionKey = encryptionKey,
      encryptionKeyVersion = raw.encryptionKeyVersion
    )
}
