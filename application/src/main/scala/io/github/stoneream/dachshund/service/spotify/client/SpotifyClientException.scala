package io.github.stoneream.dachshund.service.spotify.client

import scala.concurrent.duration.FiniteDuration

abstract sealed class SpotifyClientException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object SpotifyClientException {
  final case class Unauthorized(causeException: Throwable) extends SpotifyClientException("Spotify API が 401 を返しました", causeException)

  final case class Forbidden(causeException: Throwable) extends SpotifyClientException("Spotify API が 403 を返しました", causeException)

  final case class RateLimited(
      retryAfter: Option[FiniteDuration],
      causeException: Throwable
  ) extends SpotifyClientException("Spotify API が rate limit を返しました", causeException)

  final case class Network(causeException: Throwable) extends SpotifyClientException("Spotify API への接続に失敗しました", causeException)

  final case class ServerError(causeException: Throwable) extends SpotifyClientException("Spotify API が server error を返しました", causeException)

  final case class InvalidResponse(causeException: Throwable) extends SpotifyClientException("Spotify API のレスポンス解析に失敗しました", causeException)

  final case class ClientError(causeException: Throwable) extends SpotifyClientException("Spotify API が client error を返しました", causeException)

  final case class Unknown(causeException: Throwable) extends SpotifyClientException("Spotify API 呼び出しに失敗しました", causeException)
}
