package io.github.stoneream.dachshund.service.spotify.auth.access_token.step

import io.github.stoneream.dachshund.service.spotify.auth.access_token.context.{SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshFailureReason}
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException

import java.io.IOException
import java.net.SocketTimeoutException
import scala.util.control.NonFatal

/** Spotify アクセストークン更新失敗をリトライや再認可のために分類する。 */
private[auth] object SpotifyAccessTokenRefreshFailureClassifier {
  def fromThrowable(exception: Throwable): SpotifyAccessTokenRefreshFailure =
    exception match {
      case e: SpotifyOAuthClientException =>
        val reason = e.errorCode.map(_.trim).filter(_.nonEmpty) match {
          case Some("invalid_grant") => SpotifyAccessTokenRefreshFailureReason.InvalidGrant
          case Some("insufficient_scope") | Some("invalid_scope") => SpotifyAccessTokenRefreshFailureReason.InsufficientScope
          case Some("invalid_response") => SpotifyAccessTokenRefreshFailureReason.InvalidResponse
          case Some(_) if e.statusCode == 429 => SpotifyAccessTokenRefreshFailureReason.RateLimited
          case Some(_) if e.statusCode >= 500 => SpotifyAccessTokenRefreshFailureReason.ServerError
          case Some(_) => SpotifyAccessTokenRefreshFailureReason.ClientError
          case None if e.statusCode == 429 => SpotifyAccessTokenRefreshFailureReason.RateLimited
          case None if e.statusCode >= 500 => SpotifyAccessTokenRefreshFailureReason.ServerError
          case None => SpotifyAccessTokenRefreshFailureReason.ClientError
        }
        SpotifyAccessTokenRefreshFailure(reason = reason, retryAfter = e.error.retryAfter)
      case _: SocketTimeoutException =>
        SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.Network)
      case _: IOException =>
        SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.Network)
      case NonFatal(_) =>
        SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.Unknown)
    }

  def requiresReauthorization(reason: SpotifyAccessTokenRefreshFailureReason): Boolean =
    reason == SpotifyAccessTokenRefreshFailureReason.InvalidGrant ||
      reason == SpotifyAccessTokenRefreshFailureReason.InsufficientScope ||
      reason == SpotifyAccessTokenRefreshFailureReason.TokenDecryptFailed

  def isTemporaryFailure(reason: SpotifyAccessTokenRefreshFailureReason): Boolean =
    !requiresReauthorization(reason)
}
