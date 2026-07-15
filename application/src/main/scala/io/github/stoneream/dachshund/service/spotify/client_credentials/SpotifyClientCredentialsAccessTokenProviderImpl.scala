package io.github.stoneream.dachshund.service.spotify.client_credentials

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client_credentials.SpotifyClientCredentialsAccessTokenProvider.ResolvedSpotifyClientCredentialsAccessToken
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient

import scala.concurrent.Future
import scala.concurrent.duration.*

@Singleton
class SpotifyClientCredentialsAccessTokenProviderImpl @Inject() (
    applicationConfig: ApplicationConfig,
    spotifyOAuthClient: SpotifyOAuthClient,
    defaultExecutor: DefaultExecutor
) extends SpotifyClientCredentialsAccessTokenProvider {
  import SpotifyClientCredentialsAccessTokenProviderImpl.*

  private val tokenCache: Cache[String, ResolvedSpotifyClientCredentialsAccessToken] =
    Caffeine
      .newBuilder()
      .maximumSize(1L)
      .build[String, ResolvedSpotifyClientCredentialsAccessToken]()

  override def resolve(
      now: BusinessDateTime,
      forceRefresh: Boolean
  )(using LoggingContext): Future[ResolvedSpotifyClientCredentialsAccessToken] =
    if (!forceRefresh) {
      Option(tokenCache.getIfPresent(CacheKey)).filter(isUsable(_, now)) match {
        case Some(token) => Future.successful(token)
        case None =>
          tokenCache.invalidate(CacheKey)
          requestAndCache(now)
      }
    } else {
      tokenCache.invalidate(CacheKey)
      requestAndCache(now)
    }

  private def isUsable(
      token: ResolvedSpotifyClientCredentialsAccessToken,
      now: BusinessDateTime
  ): Boolean =
    now.isBefore(token.expiresAt.minus(applicationConfig.spotify.token.refreshMargin))

  private def requestAndCache(now: BusinessDateTime)(using LoggingContext): Future[ResolvedSpotifyClientCredentialsAccessToken] = {
    val clientConfig = applicationConfig.spotify.client

    spotifyOAuthClient
      .requestClientCredentialsAccessToken(
        clientId = clientConfig.clientId,
        clientSecret = clientConfig.clientSecret
      )
      .map { tokenResponse =>
        val resolvedToken = ResolvedSpotifyClientCredentialsAccessToken(
          accessToken = tokenResponse.accessToken,
          tokenType = tokenResponse.tokenType,
          expiresAt = now.plus(tokenResponse.expiresIn.seconds)
        )
        tokenCache.put(CacheKey, resolvedToken)
        resolvedToken
      }(using defaultExecutor)
  }
}

object SpotifyClientCredentialsAccessTokenProviderImpl {
  private val CacheKey = "client_credentials"
}
