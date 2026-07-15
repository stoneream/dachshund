package io.github.stoneream.dachshund.service.spotify.auth.access_token

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

abstract sealed class SpotifyAuthorizationCodeAccessTokenProviderException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object SpotifyAuthorizationCodeAccessTokenProviderException {
  final case class AuthorizationNotFound(userId: Long) extends SpotifyAuthorizationCodeAccessTokenProviderException("Spotify 認可情報が見つかりません")

  final case class ReauthorizationRequired(
      userId: Long,
      reasonType: String,
      causeException: Throwable = null
  ) extends SpotifyAuthorizationCodeAccessTokenProviderException("Spotify access token の再認可が必要です", causeException)

  final case class TemporaryFailure(
      userId: Long,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      causeException: Throwable = null
  ) extends SpotifyAuthorizationCodeAccessTokenProviderException("Spotify access token の一時的な解決失敗です", causeException)

  final case class ConcurrentUpdate(userId: Long) extends SpotifyAuthorizationCodeAccessTokenProviderException("Spotify 認可情報が並行更新されました")

  final case class Unknown(causeException: Throwable)
      extends SpotifyAuthorizationCodeAccessTokenProviderException("Spotify access token の解決に失敗しました", causeException)
}
