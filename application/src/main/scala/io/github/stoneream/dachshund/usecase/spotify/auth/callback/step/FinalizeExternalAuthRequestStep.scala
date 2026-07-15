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
 * 外部認証リクエストの成功または失敗結果を記録
 */
@Singleton
private[callback] class FinalizeExternalAuthRequestStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    externalAuthRequestWriter: ExternalAuthRequestWriter,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {
  def run(
      request: ExternalAuthRequest,
      now: BusinessDateTime,
      updatedUser: AuditUser = AuditUser.System
  )(using LoggingContext): Future[Unit] =
    finalizeRequest(
      request = request,
      status = ExternalAuthRequestStatus.Succeeded,
      errorCode = None,
      errorDescription = None,
      now = now,
      updatedUser = updatedUser
    )

  def run(
      request: ExternalAuthRequest,
      exception: UseCaseException,
      now: BusinessDateTime
  )(using LoggingContext): Future[Unit] =
    finalizeRequest(
      request = request,
      status = ExternalAuthRequestStatus.Failed,
      errorCode = Some(errorCode(exception)),
      errorDescription = errorDescription(exception),
      now = now
    )

  private def finalizeRequest(
      request: ExternalAuthRequest,
      status: ExternalAuthRequestStatus,
      errorCode: Option[String],
      errorDescription: Option[String],
      now: BusinessDateTime,
      updatedUser: AuditUser = AuditUser.System
  )(using LoggingContext): Future[Unit] = {
    given DefaultExecutor = defaultExecutor

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        externalAuthRequestWriter.updateStatus(
          id = request.id,
          status = status,
          expectedStatus = ExternalAuthRequestStatus.Processing,
          completedAt = Some(now),
          errorCode = errorCode,
          errorDescription = errorDescription,
          requireUnexpired = false,
          checkedAt = now,
          updatedAt = now,
          updatedUser = updatedUser
        )
      }
    }(using databaseExecutor).map { _ =>
      info(
        "Spotify 認可リクエスト結果を記録しました",
        kv("externalAuthRequestId", request.id),
        kv("status", status.dbValue),
        kv("errorCode", errorCode.getOrElse(""))
      )
    }
  }

  private def errorCode(exception: UseCaseException): String =
    exception match {
      case UseCaseException.ProviderError(errorCode) => errorCode
      case UseCaseException.AuthorizationRequestAlreadyUsed => "authorization_request_already_used"
      case UseCaseException.AuthorizationRequestExpired => "authorization_request_expired"
      case UseCaseException.TokenExchangeFailed(_) => "spotify_token_exchange_failed"
      case UseCaseException.RefreshTokenMissing => "spotify_refresh_token_missing"
      case UseCaseException.ProfileFetchFailed(_) => "spotify_profile_fetch_failed"
      case UseCaseException.AuthorizationPersistenceFailed(_, _) => "spotify_authorization_persistence_failed"
      case UseCaseException.InvalidCallback(_) => "invalid_callback"
      case UseCaseException.InvalidState => "invalid_state"
      case UseCaseException.MissingConfiguration(_) => "missing_configuration"
    }

  private def errorDescription(exception: UseCaseException): Option[String] =
    exception match {
      case UseCaseException.ProviderError(_) => Some(exception.getMessage.take(1024))
      case _ => Some(exception.getMessage.take(1024))
    }
}
