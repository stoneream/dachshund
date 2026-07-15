package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.ExternalAuthRequestWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.{ExternalAuthRequest, ExternalAuthRequestStatus}
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * 外部認証リクエストを処理中として記録し、二重使用を防止
 */
@Singleton
private[callback] class StartExternalAuthRequestStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    externalAuthRequestWriter: ExternalAuthRequestWriter,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {
  def run(
      request: ExternalAuthRequest,
      now: BusinessDateTime
  )(using LoggingContext): Future[Unit] = {
    given DefaultExecutor = defaultExecutor

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        externalAuthRequestWriter.updateStatus(
          id = request.id,
          status = ExternalAuthRequestStatus.Processing,
          expectedStatus = ExternalAuthRequestStatus.Pending,
          completedAt = None,
          errorCode = None,
          errorDescription = None,
          requireUnexpired = true,
          checkedAt = now,
          updatedAt = now,
          updatedUser = AuditUser.System
        )
      }
    }(using databaseExecutor).flatMap {
      case true =>
        info(
          "Spotify 認可リクエストの処理を開始しました",
          kv("externalAuthRequestId", request.id),
          kv("state", mask(request.state))
        )
        Future.unit
      case false =>
        info(
          "Spotify 認可リクエストの処理開始に失敗しました",
          kv("externalAuthRequestId", request.id),
          kv("state", mask(request.state))
        )
        Future.failed(UseCaseException.AuthorizationRequestAlreadyUsed)
    }
  }
}
