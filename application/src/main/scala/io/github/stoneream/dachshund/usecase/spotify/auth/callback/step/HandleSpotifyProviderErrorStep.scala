package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.SpotifyAuthCallbackValidatedInput

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotifyから返却された認可エラーをユースケース例外へ変換
 */
@Singleton
private[callback] class HandleSpotifyProviderErrorStep @Inject() () extends TraceLogger {
  def run(input: SpotifyAuthCallbackValidatedInput)(using LoggingContext): Future[Unit] =
    input.error match {
      case Some(error) =>
        info(
          "Spotify 認可がプロバイダーに拒否されました",
          kv("error", error.value)
        )
        Future.failed(
          UseCaseException.ProviderError(
            errorCode = error.value
          )
        )
      case None =>
        Future.unit
    }
}
