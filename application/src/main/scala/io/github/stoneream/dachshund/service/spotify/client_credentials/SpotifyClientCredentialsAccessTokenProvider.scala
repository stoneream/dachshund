package io.github.stoneream.dachshund.service.spotify.client_credentials

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext

import scala.concurrent.Future

trait SpotifyClientCredentialsAccessTokenProvider {
  import SpotifyClientCredentialsAccessTokenProvider.*

  def resolve(
      now: BusinessDateTime,
      forceRefresh: Boolean = false
  )(using LoggingContext): Future[ResolvedSpotifyClientCredentialsAccessToken]
}

object SpotifyClientCredentialsAccessTokenProvider {
  final case class ResolvedSpotifyClientCredentialsAccessToken(
      accessToken: String,
      tokenType: String,
      expiresAt: BusinessDateTime
  )
}
