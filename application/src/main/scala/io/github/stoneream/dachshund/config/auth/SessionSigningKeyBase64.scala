package io.github.stoneream.dachshund.config.auth

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import java.util.Base64
import scala.util.Try

final case class SessionSigningKeyBase64 private (value: String, decodedValue: Array[Byte])

object SessionSigningKeyBase64 {
  private val RequiredBytes = 32

  given ConfigReader[SessionSigningKeyBase64] =
    ConfigReader[String].emap { value =>
      val trimmedValue = value.trim
      if (trimmedValue.isEmpty) {
        Left(CannotConvert("", "SessionSigningKeyBase64", "auth.session.signing.keys.<kid> must not be blank"))
      } else {
        Try(Base64.getDecoder.decode(trimmedValue)).toEither.left
          .map(_ => CannotConvert("", "SessionSigningKeyBase64", "auth.session.signing.keys.<kid> must be base64"))
          .flatMap { decoded =>
            if (decoded.length == RequiredBytes) {
              Right(SessionSigningKeyBase64(trimmedValue, decoded))
            } else {
              Left(CannotConvert("", "SessionSigningKeyBase64", s"auth.session.signing.keys.<kid> must decode to $RequiredBytes bytes"))
            }
          }
      }
    }

  def unsafe(value: String): SessionSigningKeyBase64 =
    SessionSigningKeyBase64(value, Base64.getDecoder.decode(value))
}
