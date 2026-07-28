package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.step

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as TokenProviderException
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue.context.{UserNewReleaseNotificationDeliveryQueueFailure, UserNewReleaseNotificationDeliveryQueueFailureType}
import org.scalatest.featurespec.AnyFeatureSpec

import scala.concurrent.duration.*

class UserNewReleaseNotificationDeliveryQueueFailureClassifierSpec extends AnyFeatureSpec {
  Feature("User new release notification delivery queue failure classifier") {
    Scenario("既に queue failure の場合はそのまま返す") {
      val failure = UserNewReleaseNotificationDeliveryQueueFailure(UserNewReleaseNotificationDeliveryQueueFailureType.ReleaseTracksNotFound)

      val result = UserNewReleaseNotificationDeliveryQueueFailureClassifier.fromThrowable(failure)

      assert(result eq failure)
    }

    Scenario("Spotify access token provider の失敗を queue failure に分類する") {
      assert(
        classify(
          TokenProviderException.AuthorizationNotFound(userId = 1L)
        ).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.AuthorizationNotFound
      )
      assert(
        classify(
          TokenProviderException.ReauthorizationRequired(userId = 1L, reasonType = "invalid_grant")
        ).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.InvalidGrant
      )

      val temporaryFailure = classify(
        TokenProviderException.TemporaryFailure(
          userId = 1L,
          failureType = UserNewReleaseNotificationDeliveryQueueFailureType.Network.dbValue,
          nextAttemptAt = fixedNextAttemptAt
        )
      )
      assert(temporaryFailure.failureType == UserNewReleaseNotificationDeliveryQueueFailureType.Network)
      assert(temporaryFailure.nextAttemptAt.contains(fixedNextAttemptAt))

      assert(classify(TokenProviderException.ConcurrentUpdate(userId = 1L)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.ConcurrentUpdate)
      assert(
        classify(
          TokenProviderException.Unknown(new IllegalStateException("unexpected"))
        ).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.Unknown
      )
      assert(
        classify(
          TokenProviderException.ReauthorizationRequired(userId = 1L, reasonType = "unsupported_reason")
        ).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.Unknown
      )
    }

    Scenario("Spotify playlist API の失敗を queue failure に分類する") {
      assert(classify(ClientException.Unauthorized(cause)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.ClientError)
      assert(classify(ClientException.Forbidden(cause)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.InsufficientScope)

      val rateLimited = classify(ClientException.RateLimited(retryAfter = Some(10.seconds), causeException = cause))
      assert(rateLimited.failureType == UserNewReleaseNotificationDeliveryQueueFailureType.RateLimited)
      assert(rateLimited.retryAfter.contains(10.seconds))

      assert(classify(ClientException.Network(cause)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.Network)
      assert(classify(ClientException.ServerError(cause)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.ServerError)
      assert(classify(ClientException.InvalidResponse(cause)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.InvalidResponse)
      assert(classify(ClientException.ClientError(cause)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.PlaylistClientError)
      assert(classify(ClientException.Unknown(cause)).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.Unknown)
    }

    Scenario("想定外の例外は unknown に分類する") {
      assert(classify(new IllegalStateException("unexpected")).failureType == UserNewReleaseNotificationDeliveryQueueFailureType.Unknown)
    }
  }

  private def classify(exception: Throwable): UserNewReleaseNotificationDeliveryQueueFailure =
    UserNewReleaseNotificationDeliveryQueueFailureClassifier.fromThrowable(exception)

  private val fixedNextAttemptAt: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:10:00+09:00")

  private val cause: Throwable =
    new RuntimeException("spotify failure")
}
