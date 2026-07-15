package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotify refresh token を使って access token を更新
 */
@Singleton
private[refresh] class RequestSpotifyAccessTokenRefreshStep @Inject() (
    applicationConfig: ApplicationConfig,
    spotifyOAuthClient: SpotifyOAuthClient
) {
  def run(refreshToken: String)(using LoggingContext): Future[TokenResponse] = {
    val clientConfig = applicationConfig.spotify.client

    spotifyOAuthClient.refreshAccessToken(
      refreshToken = refreshToken,
      clientId = clientConfig.clientId,
      clientSecret = clientConfig.clientSecret
    )
  }
}
