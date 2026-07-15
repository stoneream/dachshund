package io.github.stoneream.dachshund.usecase.spotify.auth.callback

import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseInput.SpotifyAuthorizationState

abstract sealed class SpotifyAuthCallbackUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object SpotifyAuthCallbackUseCaseException {
  final case class InvalidCallback(state: Option[SpotifyAuthorizationState]) extends SpotifyAuthCallbackUseCaseException("Spotify 認可コールバックが不正です")

  case object InvalidState extends SpotifyAuthCallbackUseCaseException("Spotify 認可 state が不正です")

  case object AuthorizationRequestAlreadyUsed extends SpotifyAuthCallbackUseCaseException("Spotify 認可リクエストは使用済みです")

  case object AuthorizationRequestExpired extends SpotifyAuthCallbackUseCaseException("Spotify 認可リクエストは期限切れです")

  final case class MissingConfiguration(fieldName: String) extends SpotifyAuthCallbackUseCaseException(s"Spotify 設定が不足しています: $fieldName")

  final case class ProviderError(errorCode: String) extends SpotifyAuthCallbackUseCaseException("Spotify 認可はプロバイダーに拒否されました")

  final case class TokenExchangeFailed(causeException: Throwable) extends SpotifyAuthCallbackUseCaseException("Spotify 認可コード交換に失敗しました", causeException)

  case object RefreshTokenMissing extends SpotifyAuthCallbackUseCaseException("Spotify トークンレスポンスに refresh token が含まれていません")

  final case class ProfileFetchFailed(causeException: Throwable) extends SpotifyAuthCallbackUseCaseException("Spotify ユーザープロフィール取得に失敗しました", causeException)

  final case class AuthorizationPersistenceFailed(
      causeException: Throwable = null,
      userId: Option[Long] = None
  ) extends SpotifyAuthCallbackUseCaseException(s"Spotify 認可情報の保存に失敗しました: userId=$userId")
}
