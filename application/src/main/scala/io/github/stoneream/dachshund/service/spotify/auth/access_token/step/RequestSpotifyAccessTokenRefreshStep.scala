package io.github.stoneream.dachshund.service.spotify.auth.access_token.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/** Spotify OAuth API にアクセストークン更新を要求する。 */
@Singleton
class RequestSpotifyAccessTokenRefreshStep @Inject() (
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
