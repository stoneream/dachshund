package io.github.stoneream.dachshund.config.spotify

import pureconfig.error.CannotConvert

private[spotify] object SpotifyConfigValidation {
  def requireTrimmed(
      fieldName: String,
      value: String
  ): Either[CannotConvert, String] = {
    val trimmed = value.trim

    if (trimmed.nonEmpty) {
      Right(trimmed)
    } else {
      Left(CannotConvert("", "SpotifyConfig", s"$fieldName は空にできません"))
    }
  }
}
