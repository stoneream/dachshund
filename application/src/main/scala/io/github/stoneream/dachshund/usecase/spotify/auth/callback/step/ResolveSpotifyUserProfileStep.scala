package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse as SpotifyTokenResponse
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientException}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.SpotifyUserProfileClient.CurrentUserProfile
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Spotifyアクセストークンからユーザープロフィールを解決
 */
@Singleton
private[callback] class ResolveSpotifyUserProfileStep @Inject() (
    spotifyUserProfileClient: SpotifyUserProfileClient,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {
  def run(
      tokenResponse: SpotifyTokenResponse
  )(using LoggingContext): Future[CurrentUserProfile] =
    spotifyUserProfileClient
      .getCurrentUserProfile(tokenResponse.accessToken)
      .map { profile =>
        info(
          "Spotify ユーザープロフィールを取得しました",
          kv("spotifyUserId", mask(profile.id))
        )
        profile
      }(using defaultExecutor)
      .recoverWith {
        case e: SpotifyUserProfileClientException.ProfileFetchFailed =>
          Future.failed(UseCaseException.ProfileFetchFailed(e))
        case NonFatal(e) =>
          Future.failed(UseCaseException.ProfileFetchFailed(e))
      }(using defaultExecutor)
}
