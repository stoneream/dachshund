package io.github.stoneream.dachshund.service.spotify.oauth_client

import io.circe.parser.decode
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import SpotifyOAuthClientException.SpotifyApiClientError
import SpotifyOAuthClient.{ErrorResponse, TokenResponse}
import sttp.client3.circe.asJson
import sttp.client3.{DeserializationException, HttpClientFutureBackend, HttpError, Response, ResponseException, SttpBackendOptions, UriContext, basicRequest}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

@Singleton
class SpotifyOAuthClientImpl @Inject() (
    applicationConfig: ApplicationConfig,
    ioDispatcher: IoDispatcher
) extends SpotifyOAuthClient
    with TraceLogger {
  private given ExecutionContext = ioDispatcher
  private val clientConfig = applicationConfig.spotify.client
  private val backend = HttpClientFutureBackend(
    options = SttpBackendOptions.connectionTimeout(clientConfig.connectTimeout)
  )
  private val invalidResponseErrorCode = "invalid_response"

  override def accessTokenRequest(
      code: String,
      redirectUri: String,
      clientId: String,
      clientSecret: String
  )(using LoggingContext): Future[TokenResponse] = {
    val endpointName = "accounts-token"
    val endpoint = tokenEndpoint

    basicRequest
      .post(uri"$endpoint")
      .auth
      .basic(clientId, clientSecret)
      .readTimeout(clientConfig.requestTimeout)
      .body(
        Map(
          "grant_type" -> "authorization_code",
          "code" -> code,
          "redirect_uri" -> redirectUri
        )
      )
      .response(asJson[TokenResponse])
      .send(backend)
      .flatMap(handleResponse(_, endpointName, SpotifyOAuthClientException.TokenExchangeFailed.apply))
  }

  override def refreshAccessToken(
      refreshToken: String,
      clientId: String,
      clientSecret: String
  )(using LoggingContext): Future[TokenResponse] = {
    val endpointName = "accounts-token-refresh"
    val endpoint = tokenEndpoint

    basicRequest
      .post(uri"$endpoint")
      .auth
      .basic(clientId, clientSecret)
      .readTimeout(clientConfig.requestTimeout)
      .body(
        Map(
          "grant_type" -> "refresh_token",
          "refresh_token" -> refreshToken
        )
      )
      .response(asJson[TokenResponse])
      .send(backend)
      .flatMap(handleResponse(_, endpointName, SpotifyOAuthClientException.TokenRefreshFailed.apply))
  }

  override def requestClientCredentialsAccessToken(
      clientId: String,
      clientSecret: String
  )(using LoggingContext): Future[TokenResponse] = {
    val endpointName = "accounts-token-client-credentials"
    val endpoint = tokenEndpoint

    basicRequest
      .post(uri"$endpoint")
      .auth
      .basic(clientId, clientSecret)
      .readTimeout(clientConfig.requestTimeout)
      .body(
        Map(
          "grant_type" -> "client_credentials"
        )
      )
      .response(asJson[TokenResponse])
      .send(backend)
      .flatMap(handleResponse(_, endpointName, SpotifyOAuthClientException.ClientCredentialsTokenRequestFailed.apply))
  }

  private def tokenEndpoint: String =
    s"${clientConfig.accountsBaseUrl.stripSuffix("/")}/api/token"

  private def handleResponse[A](
      response: Response[Either[ResponseException[String, io.circe.Error], A]],
      endpointName: String,
      errorFactory: SpotifyApiClientError => SpotifyOAuthClientException
  )(using LoggingContext): Future[A] =
    response.body match {
      case Right(value) =>
        Future.successful(value)
      case Left(DeserializationException(_, error)) =>
        Future.failed(
          errorFactory(
            SpotifyApiClientError(
              endpoint = endpointName,
              statusCode = response.code.code,
              errorCode = Some(invalidResponseErrorCode),
              errorDescription = Some(error.getMessage)
            )
          )
        )
      case Left(HttpError(body, statusCode)) =>
        val parsedError = decode[ErrorResponse](body).toOption
        val errorCode = parsedError.flatMap(_.error)
        val errorDescription = parsedError.flatMap(_.errorDescription)
        val retryAfter = response.header("Retry-After").flatMap(parseRetryAfter)
        info(
          "Spotify OAuth リクエストが失敗しました",
          kv("endpoint", endpointName),
          kv("statusCode", statusCode.code),
          kv("errorCode", errorCode.getOrElse(""))
        )
        Future.failed(
          errorFactory(
            SpotifyApiClientError(
              endpoint = endpointName,
              statusCode = statusCode.code,
              errorCode = errorCode,
              errorDescription = errorDescription,
              retryAfter = retryAfter
            )
          )
        )
    }

  private def parseRetryAfter(value: String): Option[FiniteDuration] =
    value.trim.toLongOption.filter(_ >= 0L).map(_.seconds)
}
