package io.github.stoneream.dachshund.service.spotify.client.lib

import com.google.inject.{Inject, Singleton}
import io.circe.Error
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestThrottler.Throttled
import org.apache.hc.core5.http.ParseException
import se.michaelthelin.spotify.exceptions.detailed.{BadGatewayException, ForbiddenException, InternalServerErrorException, ServiceUnavailableException, TooManyRequestsException, UnauthorizedException}
import se.michaelthelin.spotify.{SpotifyApi, SpotifyHttpManager}
import sttp.client3.{DeserializationException, HttpClientFutureBackend, HttpError, Response, ResponseException, SttpBackend, SttpBackendOptions}

import java.io.IOException
import java.net.URI
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
private[client] class SpotifyRequestExecutor @Inject() (
    applicationConfig: ApplicationConfig,
    ioDispatcher: IoDispatcher,
    requestThrottler: SpotifyRequestThrottler
) extends TraceLogger {
  private given ExecutionContext = ioDispatcher
  private val clientConfig = applicationConfig.spotify.client
  private val spotifyApiBaseUri = URI.create(clientConfig.apiBaseUrl)
  private val spotifyApiPort =
    Option(spotifyApiBaseUri.getPort)
      .filter(_ >= 0)
      .getOrElse(if (spotifyApiBaseUri.getScheme == "http") 80 else 443)
  private val httpManager =
    new SpotifyHttpManager.Builder()
      .setConnectionRequestTimeout(toMillisInt(clientConfig.connectTimeout.toMillis))
      .setSocketTimeout(toMillisInt(clientConfig.requestTimeout.toMillis))
      .build()
  private val backend = HttpClientFutureBackend(
    options = SttpBackendOptions.connectionTimeout(clientConfig.connectTimeout)
  )

  def spotifyApi(accessToken: String): SpotifyApi =
    SpotifyApi
      .builder()
      .setAccessToken(accessToken)
      .setHttpManager(httpManager)
      .setScheme(spotifyApiBaseUri.getScheme)
      .setHost(spotifyApiBaseUri.getHost)
      .setPort(spotifyApiPort)
      .build()

  def executeSdk[A](
      endpointName: String
  )(
      request: => A
  )(using LoggingContext): Future[A] =
    requestThrottler.acquirePermit().flatMap {
      case Left(throttled) =>
        Future.failed(throttledException(throttled))
      case Right(_) =>
        val startedAtNanos = System.nanoTime()
        Future(request)(using ioDispatcher).map { response =>
          debug(
            "Spotify API リクエストが完了しました",
            kv("endpoint", endpointName),
            kv("elapsedMillis", elapsedMillis(startedAtNanos))
          )
          response
        }
    }

  def executeJson[A](
      endpointName: String
  )(
      request: SttpBackend[Future, Any] => Future[Response[Either[ResponseException[String, Error], A]]]
  )(using LoggingContext): Future[A] =
    send(endpointName)(request).flatMap(handleJsonResponse(_, endpointName))

  def executeEmpty(
      endpointName: String
  )(
      request: SttpBackend[Future, Any] => Future[Response[Either[String, String]]]
  )(using LoggingContext): Future[Unit] =
    send(endpointName)(request).flatMap(handleEmptyResponse(_, endpointName))

  def recoverFailures[A](result: Future[A]): Future[A] =
    result.recoverWith { case NonFatal(exception) =>
      Future.failed(classify(exception))
    }

  private def send[A](
      endpointName: String
  )(
      request: SttpBackend[Future, Any] => Future[Response[A]]
  )(using LoggingContext): Future[Response[A]] =
    requestThrottler.acquirePermit().flatMap {
      case Left(throttled) =>
        Future.failed(throttledException(throttled))
      case Right(_) =>
        val startedAtNanos = System.nanoTime()
        request(backend).map { response =>
          debug(
            "Spotify API リクエストが完了しました",
            kv("endpoint", endpointName),
            kv("statusCode", response.code.code),
            kv("elapsedMillis", elapsedMillis(startedAtNanos))
          )
          response
        }
    }

  private def handleJsonResponse[A](
      response: Response[Either[ResponseException[String, Error], A]],
      endpointName: String
  )(using LoggingContext): Future[A] =
    response.body match {
      case Right(value) =>
        Future.successful(value)
      case Left(DeserializationException(_, error)) =>
        Future.failed(SpotifyClientException.InvalidResponse(error))
      case Left(HttpError(_, statusCode)) =>
        Future.failed(
          classifyStatusAndLog(
            endpointName = endpointName,
            statusCode = statusCode.code,
            retryAfter = response.header("Retry-After").flatMap(parseRetryAfter)
          )
        )
    }

  private def handleEmptyResponse(
      response: Response[Either[String, String]],
      endpointName: String
  )(using LoggingContext): Future[Unit] =
    response.body match {
      case Right(_) =>
        Future.unit
      case Left(_) =>
        Future.failed(
          classifyStatusAndLog(
            endpointName = endpointName,
            statusCode = response.code.code,
            retryAfter = response.header("Retry-After").flatMap(parseRetryAfter)
          )
        )
    }

  private def classifyStatusAndLog(
      endpointName: String,
      statusCode: Int,
      retryAfter: Option[FiniteDuration]
  )(using LoggingContext): SpotifyClientException = {
    val exception = classifyStatus(
      endpointName = endpointName,
      statusCode = statusCode,
      retryAfter = retryAfter
    )
    exception match {
      case SpotifyClientException.RateLimited(effectiveRetryAfter, _) =>
        warn(
          "Spotify API の rate limit に達しました",
          kv("endpoint", endpointName),
          kv("statusCode", statusCode),
          kv("retryAfter", effectiveRetryAfter.map(_.toString).getOrElse(""))
        )
      case _ =>
        info(
          "Spotify API リクエストが失敗しました",
          kv("endpoint", endpointName),
          kv("statusCode", statusCode)
        )
    }
    exception
  }

  private def classifyStatus(
      endpointName: String,
      statusCode: Int,
      retryAfter: Option[FiniteDuration]
  ): SpotifyClientException = {
    val cause = SpotifyWebApiStatusException(endpointName, statusCode)
    statusCode match {
      case 401 => SpotifyClientException.Unauthorized(cause)
      case 403 => SpotifyClientException.Forbidden(cause)
      case 429 =>
        val effectiveDelay = retryAfter.getOrElse(clientConfig.requestPolicy.rateLimitFallbackDelay)
        requestThrottler.registerRateLimit(effectiveDelay)
        SpotifyClientException.RateLimited(Some(effectiveDelay), cause)
      case status if status >= 500 => SpotifyClientException.ServerError(cause)
      case status if status >= 400 => SpotifyClientException.ClientError(cause)
      case _ => SpotifyClientException.Unknown(cause)
    }
  }

  private def parseRetryAfter(value: String): Option[FiniteDuration] =
    value.trim.toLongOption.filter(_ >= 0L).map(_.seconds)

  private def classify(exception: Throwable): SpotifyClientException =
    exception match {
      case e: SpotifyClientException =>
        e
      case e: UnauthorizedException =>
        SpotifyClientException.Unauthorized(e)
      case e: ForbiddenException =>
        SpotifyClientException.Forbidden(e)
      case e: TooManyRequestsException =>
        val retryAfter = Option(e.getRetryAfter).filter(_ > 0).map(_.seconds)
        val effectiveDelay = retryAfter.getOrElse(clientConfig.requestPolicy.rateLimitFallbackDelay)
        requestThrottler.registerRateLimit(effectiveDelay)
        SpotifyClientException.RateLimited(Some(effectiveDelay), e)
      case e: InternalServerErrorException =>
        SpotifyClientException.ServerError(e)
      case e: BadGatewayException =>
        SpotifyClientException.ServerError(e)
      case e: ServiceUnavailableException =>
        SpotifyClientException.ServerError(e)
      case e: IOException =>
        SpotifyClientException.Network(e)
      case e: ParseException =>
        SpotifyClientException.InvalidResponse(e)
      case e: se.michaelthelin.spotify.exceptions.SpotifyWebApiException =>
        SpotifyClientException.ClientError(e)
      case NonFatal(e) =>
        SpotifyClientException.Unknown(e)
    }

  private def throttledException(throttled: Throttled): SpotifyClientException =
    SpotifyClientException.RateLimited(
      retryAfter = Some(throttled.retryAfter),
      causeException = SpotifyRequestThrottledException()
    )

  private def elapsedMillis(startedAtNanos: Long): Long =
    (System.nanoTime() - startedAtNanos).nanos.toMillis

  private def toMillisInt(value: Long): Integer =
    math.min(value, Int.MaxValue.toLong).toInt

  private final case class SpotifyWebApiStatusException(
      endpointName: String,
      statusCode: Int
  ) extends RuntimeException(s"Spotify API request failed: endpoint=$endpointName, statusCode=$statusCode")

  private final case class SpotifyRequestThrottledException() extends RuntimeException("Spotify API request throttled by rate limit")
}
