package io.github.stoneream.dachshund.service.spotify.oauth_client

import io.circe.Decoder
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext

import scala.concurrent.Future

trait SpotifyOAuthClient {
  import SpotifyOAuthClient.*

  /**
   * Authorization Code Flow でアクセストークンを要求する
   */
  def accessTokenRequest(
      code: String,
      redirectUri: String,
      clientId: String,
      clientSecret: String
  )(using LoggingContext): Future[TokenResponse]

  /**
   * Refresh Token を使ってアクセストークンを更新する。
   */
  def refreshAccessToken(
      refreshToken: String,
      clientId: String,
      clientSecret: String
  )(using LoggingContext): Future[TokenResponse]

  /**
   * Client Credentials Flow でアクセストークンを要求する
   */
  def requestClientCredentialsAccessToken(
      clientId: String,
      clientSecret: String
  )(using LoggingContext): Future[TokenResponse]
}

object SpotifyOAuthClient {
  final case class TokenResponse(
      accessToken: String,
      tokenType: String,
      expiresIn: Long,
      refreshToken: Option[String],
      scope: Option[String]
  )

  object TokenResponse {
    given Decoder[TokenResponse] =
      Decoder.forProduct5(
        "access_token",
        "token_type",
        "expires_in",
        "refresh_token",
        "scope"
      )(TokenResponse.apply)
  }

  final case class ErrorResponse(
      error: Option[String],
      errorDescription: Option[String]
  )

  object ErrorResponse {
    given Decoder[ErrorResponse] =
      Decoder.forProduct2("error", "error_description")(ErrorResponse.apply)
  }
}
