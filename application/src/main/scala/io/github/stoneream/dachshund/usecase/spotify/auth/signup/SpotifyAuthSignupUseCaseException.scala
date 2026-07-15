package io.github.stoneream.dachshund.usecase.spotify.auth.signup

abstract sealed class SpotifyAuthSignupUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object SpotifyAuthSignupUseCaseException {
  final case class MissingConfiguration(fieldName: String) extends SpotifyAuthSignupUseCaseException(s"Spotify 設定が不足しています: $fieldName")
}
