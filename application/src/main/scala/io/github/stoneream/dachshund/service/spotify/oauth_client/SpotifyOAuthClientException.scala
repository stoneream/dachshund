package io.github.stoneream.dachshund.service.spotify.oauth_client

import SpotifyOAuthClientException.SpotifyApiClientError

import scala.concurrent.duration.FiniteDuration

abstract sealed class SpotifyOAuthClientException(
    override val getMessage: String,
    val error: SpotifyApiClientError
) extends RuntimeException(getMessage) {
  val endpoint: String = error.endpoint
  val statusCode: Int = error.statusCode
  val errorCode: Option[String] = error.errorCode
  val errorDescription: Option[String] = error.errorDescription
}

object SpotifyOAuthClientException {
  final case class SpotifyApiClientError(
      endpoint: String,
      statusCode: Int,
      errorCode: Option[String],
      errorDescription: Option[String],
      retryAfter: Option[FiniteDuration] = None
  )

  final case class TokenExchangeFailed(
      override val error: SpotifyApiClientError
  ) extends SpotifyOAuthClientException(
        "Spotify アクセストークンリクエストに失敗しました",
        error
      )

  final case class TokenRefreshFailed(
      override val error: SpotifyApiClientError
  ) extends SpotifyOAuthClientException(
        "Spotify アクセストークン更新リクエストに失敗しました",
        error
      )

  final case class ClientCredentialsTokenRequestFailed(
      override val error: SpotifyApiClientError
  ) extends SpotifyOAuthClientException(
        "Spotify client credentials アクセストークンリクエストに失敗しました",
        error
      )
}
