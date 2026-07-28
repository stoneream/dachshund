package io.github.stoneream.dachshund.service.spotify.user_profile_client

import io.circe.parser.decode
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestThrottler
import io.github.stoneream.dachshund.service.spotify.user_profile_client.SpotifyUserProfileClient.{CurrentUserProfile, ErrorResponse}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.SpotifyUserProfileClientException.SpotifyApiClientError
import sttp.client3.circe.asJson
import sttp.client3.{DeserializationException, HttpClientFutureBackend, HttpError, Response, ResponseException, SttpBackendOptions, UriContext, basicRequest}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

@Singleton
class SpotifyUserProfileClientImpl @Inject() (
    applicationConfig: ApplicationConfig,
    ioDispatcher: IoDispatcher,
    requestThrottler: SpotifyRequestThrottler
) extends SpotifyUserProfileClient
    with TraceLogger {
  private given ExecutionContext = ioDispatcher
  private val clientConfig = applicationConfig.spotify.client
  private val backend = HttpClientFutureBackend(
    options = SttpBackendOptions.connectionTimeout(clientConfig.connectTimeout)
  )
  private val endpointName = "api-current-user-profile"
  private val invalidResponseErrorCode = "invalid_response"

  override def getCurrentUserProfile(accessToken: String)(using LoggingContext): Future[CurrentUserProfile] = {
    val endpoint = s"${clientConfig.apiBaseUrl.stripSuffix("/")}/me"

    requestThrottler.acquirePermit().flatMap {
      case Left(_) =>
        Future.failed(throttledException)
      case Right(_) =>
        basicRequest
          .get(uri"$endpoint")
          .auth
          .bearer(accessToken)
          .readTimeout(clientConfig.requestTimeout)
          .response(asJson[CurrentUserProfile])
          .send(backend)
          .flatMap(handleResponse)
    }
  }

  private def handleResponse(
      response: Response[Either[ResponseException[String, io.circe.Error], CurrentUserProfile]]
  )(using LoggingContext): Future[CurrentUserProfile] =
    response.body match {
      case Right(value) =>
        Future.successful(value)
      case Left(DeserializationException(_, error)) =>
        Future.failed(
          SpotifyUserProfileClientException.ProfileFetchFailed(
            SpotifyApiClientError(
              endpoint = endpointName,
              statusCode = response.code.code,
              errorCode = Some(invalidResponseErrorCode),
              errorDescription = Some(error.getMessage)
            )
          )
        )
      case Left(HttpError(body, statusCode)) =>
        val parsedError = decode[ErrorResponse](body).toOption.flatMap(_.error)
        val errorDescription = parsedError.flatMap(_.message)
        if (statusCode.code == 429) {
          val retryAfter = response
            .header("Retry-After")
            .flatMap(parseRetryAfter)
            .getOrElse(clientConfig.requestPolicy.rateLimitFallbackDelay)
          requestThrottler.registerRateLimit(retryAfter)
        }
        info(
          "Spotify ユーザープロフィールリクエストが失敗しました",
          kv("endpoint", endpointName),
          kv("statusCode", statusCode.code),
          kv("errorDescription", errorDescription.getOrElse(""))
        )
        Future.failed(
          SpotifyUserProfileClientException.ProfileFetchFailed(
            SpotifyApiClientError(
              endpoint = endpointName,
              statusCode = statusCode.code,
              errorCode = parsedError.flatMap(_.status).map(_.toString),
              errorDescription = errorDescription
            )
          )
        )
    }

  private def throttledException: SpotifyUserProfileClientException.ProfileFetchFailed =
    SpotifyUserProfileClientException.ProfileFetchFailed(
      SpotifyApiClientError(
        endpoint = endpointName,
        statusCode = 429,
        errorCode = Some("rate_limited"),
        errorDescription = None
      )
    )

  private def parseRetryAfter(value: String): Option[FiniteDuration] =
    value.trim.toLongOption.filter(_ >= 0L).map(_.seconds)
}
