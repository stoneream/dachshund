package io.github.stoneream.dachshund.usecase.user_settings.apply.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenResolveInput}

import scala.concurrent.Future

@Singleton
private[apply] class ResolveSpotifyAccessTokenStep @Inject() (
    authorizationCodeAccessTokenProvider: SpotifyAuthorizationCodeAccessTokenProvider,
    defaultExecutor: DefaultExecutor
) {
  def run(
      userId: Long,
      now: BusinessDateTime
  )(using LoggingContext): Future[String] =
    authorizationCodeAccessTokenProvider
      .resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, now, forceRefresh = false))
      .map(_.accessToken)(using defaultExecutor)
}
