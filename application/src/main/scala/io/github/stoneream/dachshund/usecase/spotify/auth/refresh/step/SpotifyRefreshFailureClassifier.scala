package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.{SpotifyRefreshFailure, SpotifyRefreshFailureType}

import java.io.IOException
import java.net.SocketTimeoutException
import scala.util.control.NonFatal

private[refresh] object SpotifyRefreshFailureClassifier {
  private val InvalidClientErrorCode = "invalid_client"

  def fromThrowable(exception: Throwable): SpotifyRefreshFailure = exception match {
    case e: SpotifyOAuthClientException =>
      val failureType = e.errorCode.map(_.trim).filter(_.nonEmpty) match {
        case Some("invalid_grant") => SpotifyRefreshFailureType.InvalidGrant
        case Some("insufficient_scope") | Some("invalid_scope") => SpotifyRefreshFailureType.InsufficientScope
        case Some("invalid_response") => SpotifyRefreshFailureType.InvalidResponse
        case Some(_) if e.statusCode == 429 => SpotifyRefreshFailureType.RateLimited
        case Some(_) if e.statusCode >= 500 => SpotifyRefreshFailureType.ServerError
        case Some(_) => SpotifyRefreshFailureType.ClientError
        case None if e.statusCode == 429 => SpotifyRefreshFailureType.RateLimited
        case None if e.statusCode >= 500 => SpotifyRefreshFailureType.ServerError
        case None => SpotifyRefreshFailureType.ClientError
      }
      SpotifyRefreshFailure(failureType = failureType, retryAfter = e.error.retryAfter)
    case _: SocketTimeoutException =>
      SpotifyRefreshFailure(SpotifyRefreshFailureType.Network)
    case _: IOException =>
      SpotifyRefreshFailure(SpotifyRefreshFailureType.Network)
    case NonFatal(_) =>
      SpotifyRefreshFailure(SpotifyRefreshFailureType.Unknown)
  }

  def isInvalidClientCredentials(exception: Throwable): Boolean =
    exception match {
      case e: SpotifyOAuthClientException =>
        e.errorCode.map(_.trim).contains(InvalidClientErrorCode)
      case _ => false
    }

  def requiresReauthorization(failureType: String): Boolean =
    failureType == SpotifyRefreshFailureType.InvalidGrant ||
      failureType == SpotifyRefreshFailureType.InsufficientScope ||
      failureType == SpotifyRefreshFailureType.TokenDecryptFailed
}
