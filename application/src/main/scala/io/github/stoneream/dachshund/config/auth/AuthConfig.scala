package io.github.stoneream.dachshund.config.auth

import pureconfig.ConfigReader

final case class AuthConfig(
    session: SessionConfig
)

object AuthConfig {
  given ConfigReader[AuthConfig] =
    ConfigReader.forProduct1("session")(AuthConfig.apply)
}
