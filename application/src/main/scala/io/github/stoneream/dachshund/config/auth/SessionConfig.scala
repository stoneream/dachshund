package io.github.stoneream.dachshund.config.auth

import pureconfig.ConfigReader

final case class SessionConfig(
    signing: SessionSigningConfig
)

object SessionConfig {
  given ConfigReader[SessionConfig] =
    ConfigReader.forProduct1("signing")(SessionConfig.apply)
}
