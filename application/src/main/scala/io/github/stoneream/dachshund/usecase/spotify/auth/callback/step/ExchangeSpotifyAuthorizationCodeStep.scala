package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse as SpotifyTokenResponse
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseInput.SpotifyAuthorizationCode

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Spotifyの認可コードをアクセストークンへ交換
 */
@Singleton
private[callback] class ExchangeSpotifyAuthorizationCodeStep @Inject() (
    applicationConfig: ApplicationConfig,
    spotifyOAuthClient: SpotifyOAuthClient,
    defaultExecutor: DefaultExecutor
) {
  def run(
      code: SpotifyAuthorizationCode,
      redirectUri: String
  )(using LoggingContext): Future[SpotifyTokenResponse] = {
    val clientConfig = applicationConfig.spotify.client

    spotifyOAuthClient
      .accessTokenRequest(
        code = code.value,
        redirectUri = redirectUri,
        clientId = clientConfig.clientId,
        clientSecret = clientConfig.clientSecret
      )
      .recoverWith { case NonFatal(e) =>
        Future.failed(UseCaseException.TokenExchangeFailed(e))
      }(using defaultExecutor)
  }
}
