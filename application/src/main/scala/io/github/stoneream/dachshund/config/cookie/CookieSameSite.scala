package io.github.stoneream.dachshund.config.cookie

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

final case class CookieSameSite private (value: String)

object CookieSameSite {
  given ConfigReader[CookieSameSite] =
    ConfigReader[String].emap { value =>
      val normalizedValue = value.trim.toLowerCase
      normalizedValue match {
        case "lax" | "strict" | "none" => Right(CookieSameSite(normalizedValue))
        case _ => Left(CannotConvert("", "CookieSameSite", "cookie.<type>.same-site must be lax, strict, or none"))
      }
    }

  def unsafe(value: String): CookieSameSite =
    CookieSameSite(value)
}
