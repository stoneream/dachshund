package io.github.stoneream.dachshund.usecase.daemon.ready

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.daemon.ready.{DaemonReadyUseCaseException as UseCaseException, DaemonReadyUseCaseInput as UseCaseInput, DaemonReadyUseCaseOutput as UseCaseOutput}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class DaemonReadyUseCase @Inject() (
    applicationConfig: ApplicationConfig,
    spotifyOAuthClient: SpotifyOAuthClient,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    debug("daemon readiness check", kv("input", input))

    val clientConfig = applicationConfig.spotify.client

    spotifyOAuthClient
      .requestClientCredentialsAccessToken(
        clientId = clientConfig.clientId,
        clientSecret = clientConfig.clientSecret
      )
      .map(_ => UseCaseOutput())
      .recoverWith { case NonFatal(exception) =>
        warn(
          "daemon 起動時の Spotify client credentials 検証に失敗しました",
          kv("daemon.failureClass", exception.getClass.getName)
        )
        Future.failed(UseCaseException.Unavailable(exception))
      }
  }
}
