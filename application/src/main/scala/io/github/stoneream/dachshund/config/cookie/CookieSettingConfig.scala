package io.github.stoneream.dachshund.config.cookie

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

final case class CookieSettingConfig(
    name: String,
    secure: Boolean,
    sameSite: CookieSameSite,
    domain: Option[String],
    maxAgeSeconds: Option[Long]
)

object CookieSettingConfig {
  private final case class RawCookieSettingConfig(
      name: String,
      secure: Boolean,
      sameSite: CookieSameSite,
      domain: Option[String],
      maxAgeSeconds: Option[Long]
  )

  given ConfigReader[CookieSettingConfig] =
    ConfigReader
      .forProduct5("name", "secure", "same-site", "domain", "max-age-seconds")(RawCookieSettingConfig.apply)
      .emap(validate)

  private def validate(raw: RawCookieSettingConfig): Either[CannotConvert, CookieSettingConfig] =
    for {
      name <- validateRequiredTrimmedText("cookie.<type>.name", raw.name)
      domain <- validateOptionalTrimmedText("cookie.<type>.domain", raw.domain)
      maxAgeSeconds <- validateMaxAgeSeconds(raw.maxAgeSeconds)
    } yield {
      CookieSettingConfig(
        name = name,
        secure = raw.secure,
        sameSite = raw.sameSite,
        domain = domain,
        maxAgeSeconds = maxAgeSeconds
      )
    }

  private def validateOptionalTrimmedText(fieldName: String, value: Option[String]): Either[CannotConvert, Option[String]] =
    value match {
      case None => Right(None)
      case Some(text) => validateRequiredTrimmedText(fieldName, text).map(Some(_))
    }

  private def validateRequiredTrimmedText(fieldName: String, value: String): Either[CannotConvert, String] = {
    val trimmedValue = value.trim
    if (trimmedValue.isEmpty) {
      Left(CannotConvert("", "CookieSettingConfig", s"$fieldName は空にできません"))
    } else if (trimmedValue != value) {
      Left(CannotConvert("", "CookieSettingConfig", s"$fieldName に前後の空白は使えません"))
    } else {
      Right(value)
    }
  }

  private def validateMaxAgeSeconds(value: Option[Long]): Either[CannotConvert, Option[Long]] =
    value match {
      case Some(seconds) if seconds <= 0L =>
        Left(CannotConvert("", "CookieSettingConfig", "cookie.<type>.max-age-seconds は 1 以上にしてください"))
      case _ => Right(value)
    }
}
