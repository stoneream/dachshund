package io.github.stoneream.dachshund.usecase.spotify.auth.signup.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext

import java.security.SecureRandom
import java.util.Base64
import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotifyサインアップ開始に必要な認可リクエストを発行
 */
@Singleton
private[signup] class BuildSpotifyAuthorizationRequestStep @Inject() (
    applicationConfig: ApplicationConfig
) extends TraceLogger {
  private val spotifyClientConfig = applicationConfig.spotify.client
  private val secureRandom = new SecureRandom()
  private val base64UrlEncoder = Base64.getUrlEncoder.withoutPadding()

  def run()(using LoggingContext): Future[SpotifyAuthorizationRequest] = {
    val state = generateState()
    val scopeText = SpotifyRequiredScopes.ScopeText

    info(
      "Spotify 認可リクエストを構築しました",
      kv("scope", scopeText)
    )

    Future.successful(
      SpotifyAuthorizationRequest(
        authorizationEndpoint = s"${spotifyClientConfig.accountsBaseUrl.stripSuffix("/")}/authorize",
        clientId = spotifyClientConfig.clientId,
        state = state,
        scopeText = scopeText,
        redirectUri = spotifyClientConfig.redirectUri
      )
    )
  }

  private def generateState(): String = {
    val bytes = new Array[Byte](32)
    secureRandom.nextBytes(bytes)
    base64UrlEncoder.encodeToString(bytes)
  }
}
