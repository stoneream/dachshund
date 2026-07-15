package io.github.stoneream.dachshund.service.spotify.auth.access_token

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext

import scala.concurrent.Future

trait SpotifyAuthorizationCodeAccessTokenProvider {
  def resolve(
      input: SpotifyAuthorizationCodeAccessTokenResolveInput
  )(using LoggingContext): Future[SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken]
}

object SpotifyAuthorizationCodeAccessTokenProvider {
  final case class ResolvedSpotifyAuthorizationCodeAccessToken(
      accessToken: String,
      tokenType: String,
      scopeText: String,
      expiresAt: BusinessDateTime
  )
}
