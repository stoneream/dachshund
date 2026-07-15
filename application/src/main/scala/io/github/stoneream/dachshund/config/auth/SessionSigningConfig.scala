package io.github.stoneream.dachshund.config.auth

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

final case class SessionSigningConfig(
    currentKid: String,
    keys: Map[String, SessionSigningKeyBase64]
)

object SessionSigningConfig {
  private final case class RawSessionSigningConfig(
      currentKid: String,
      keys: Map[String, SessionSigningKeyBase64]
  )

  given ConfigReader[SessionSigningConfig] =
    ConfigReader
      .forProduct2("current-kid", "keys")(RawSessionSigningConfig.apply)
      .emap(validate)

  private def validate(raw: RawSessionSigningConfig): Either[CannotConvert, SessionSigningConfig] = {
    val currentKid = raw.currentKid.trim
    if (currentKid.isEmpty) {
      Left(CannotConvert("", "SessionSigningConfig", "auth.session.signing.current-kid must not be blank"))
    } else if (!raw.keys.contains(currentKid)) {
      Left(CannotConvert("", "SessionSigningConfig", "auth.session.signing.current-kid must exist in keys"))
    } else {
      Right(SessionSigningConfig(currentKid = currentKid, keys = raw.keys))
    }
  }
}
