package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.ExternalAuthRequest
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.SpotifyAuthCallbackValidatedInput

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.auth.callback.ExternalAuthRequestReader
import scala.concurrent.Future

/**
 * 認可レスポンスに紐づく外部認証リクエストの解決
 */
@Singleton
private[callback] class ResolveExternalAuthStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    externalAuthRequestReader: ExternalAuthRequestReader,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {

  import ResolveExternalAuthStep.*

  def run(input: SpotifyAuthCallbackValidatedInput)(using LoggingContext): Future[ResolvedExternalAuth] = {
    given DefaultExecutor = defaultExecutor

    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { session =>
        externalAuthRequestReader.findByState(input.state.value)(using session)
      }
    }(using databaseExecutor).flatMap {
      case Some(expectedExternalAuthRequest) =>
        info(
          "Spotify 認可リクエストを特定しました",
          kv("externalAuthRequestId", expectedExternalAuthRequest.id)
        )
        Future.successful(
          ResolvedExternalAuth(
            externalAuthRequest = expectedExternalAuthRequest
          )
        )
      case None =>
        info(
          "Spotify 認可コールバックを拒否しました",
          kv("state", mask(input.state.value))
        )
        Future.failed(UseCaseException.InvalidState)
    }
  }
}

private[callback] object ResolveExternalAuthStep {
  final case class ResolvedExternalAuth(
      externalAuthRequest: ExternalAuthRequest
  )
}
