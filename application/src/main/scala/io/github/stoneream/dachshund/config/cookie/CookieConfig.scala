package io.github.stoneream.dachshund.config.cookie

import pureconfig.ConfigReader

final case class CookieConfig(
    session: CookieSettingConfig,
    externalAuthState: CookieSettingConfig
)

object CookieConfig {
  given ConfigReader[CookieConfig] =
    ConfigReader
      .forProduct2("session", "external-auth-state")(CookieConfig.apply)
}
