package io.github.stoneream.dachshund.service.spotify.auth.access_token

import io.github.stoneream.dachshund.service.spotify.auth.access_token.context.SpotifyAccessTokenRefreshFailureReason
import io.github.stoneream.dachshund.service.spotify.auth.access_token.step.SpotifyAccessTokenRefreshFailureClassifier
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException.SpotifyApiClientError
import org.scalatest.featurespec.AnyFeatureSpec

import java.net.SocketTimeoutException
import scala.concurrent.duration.*

class SpotifyAccessTokenRefreshFailureClassifierSpec extends AnyFeatureSpec {
  Feature("Spotify access token refresh failure classifier") {
    Scenario("OAuth refresh 失敗と network 失敗を provider 用 reason に分類する") {
      assert(classify(statusCode = 400, errorCode = Some("invalid_grant")).reason == SpotifyAccessTokenRefreshFailureReason.InvalidGrant)
      assert(classify(statusCode = 400, errorCode = Some("invalid_scope")).reason == SpotifyAccessTokenRefreshFailureReason.InsufficientScope)

      val rateLimited = classify(statusCode = 429, errorCode = None, retryAfter = Some(10.seconds))
      assert(rateLimited.reason == SpotifyAccessTokenRefreshFailureReason.RateLimited)
      assert(rateLimited.retryAfter.contains(10.seconds))

      assert(classify(statusCode = 503, errorCode = None).reason == SpotifyAccessTokenRefreshFailureReason.ServerError)
      assert(
        SpotifyAccessTokenRefreshFailureClassifier.fromThrowable(new SocketTimeoutException("timeout")).reason ==
          SpotifyAccessTokenRefreshFailureReason.Network
      )
      assert(
        SpotifyAccessTokenRefreshFailureClassifier.fromThrowable(new IllegalStateException("unexpected")).reason ==
          SpotifyAccessTokenRefreshFailureReason.Unknown
      )
      assert(SpotifyAccessTokenRefreshFailureClassifier.isTemporaryFailure(SpotifyAccessTokenRefreshFailureReason.InvalidResponse))
      assert(SpotifyAccessTokenRefreshFailureClassifier.isTemporaryFailure(SpotifyAccessTokenRefreshFailureReason.ClientError))
      assert(SpotifyAccessTokenRefreshFailureClassifier.isTemporaryFailure(SpotifyAccessTokenRefreshFailureReason.Unknown))
    }
  }

  private def classify(
      statusCode: Int,
      errorCode: Option[String],
      retryAfter: Option[FiniteDuration] = None
  ) =
    SpotifyAccessTokenRefreshFailureClassifier.fromThrowable(
      SpotifyOAuthClientException.TokenRefreshFailed(
        SpotifyApiClientError(
          endpoint = "accounts-token-refresh",
          statusCode = statusCode,
          errorCode = errorCode,
          errorDescription = None,
          retryAfter = retryAfter
        )
      )
    )
}
