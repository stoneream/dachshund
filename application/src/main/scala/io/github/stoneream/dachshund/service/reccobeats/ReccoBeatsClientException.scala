package io.github.stoneream.dachshund.service.reccobeats

import scala.concurrent.duration.FiniteDuration

sealed abstract class ReccoBeatsClientException(message: String, cause: Throwable = null) extends Exception(message, cause)

object ReccoBeatsClientException {
  final case class AccessDenied(statusCode: Int) extends ReccoBeatsClientException(s"ReccoBeats API が $statusCode を返しました")

  final case class RateLimited(retryAfter: Option[FiniteDuration]) extends ReccoBeatsClientException("ReccoBeats API が rate limit を返しました")

  final case class HttpError(statusCode: Int) extends ReccoBeatsClientException(s"ReccoBeats API が $statusCode を返しました")

  final case class InvalidResponse(causeException: Throwable) extends ReccoBeatsClientException("ReccoBeats API の応答が不正です", causeException)

  final case class Network(causeException: Throwable) extends ReccoBeatsClientException("ReccoBeats API への接続に失敗しました", causeException)
}
