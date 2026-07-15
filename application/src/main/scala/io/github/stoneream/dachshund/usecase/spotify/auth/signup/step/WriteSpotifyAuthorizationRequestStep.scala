package io.github.stoneream.dachshund.usecase.spotify.auth.signup.step

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ExternalAuthRequestDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.ExternalAuthRequestSource
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.ExternalAuthRequestWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.{ExternalAuthFlowType, ExternalAuthProviderType, ExternalAuthRequestStatus}
import io.github.stoneream.dachshund.usecase.spotify.auth.signup.SpotifyAuthSignupUseCase

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration.*

/**
 * Spotifyサインアップ用の外部認証リクエストを永続化
 */
@Singleton
private[signup] class WriteSpotifyAuthorizationRequestStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    externalAuthRequestWriter: ExternalAuthRequestWriter,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {
  def run(
      authorizationRequest: SpotifyAuthorizationRequest,
      now: BusinessDateTime
  )(using LoggingContext): Future[Unit] = {
    given DefaultExecutor = defaultExecutor

    val expiresAt = now.plus(SpotifyAuthSignupUseCase.OAuthStateTtlSeconds.seconds)

    for {
      externalAuthRequestId <- Future {
        databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
          externalAuthRequestWriter.write(
            ExternalAuthRequestSource(
              flowType = ExternalAuthFlowType.Signup,
              providerType = ExternalAuthProviderType.Spotify,
              state = authorizationRequest.state,
              nonce = "",
              codeVerifier = None,
              redirectUri = authorizationRequest.redirectUri,
              scopes = authorizationRequest.scopeText,
              status = ExternalAuthRequestStatus.Pending,
              expiresAt = expiresAt,
              completedAt = Option.empty,
              errorCode = Option.empty,
              errorDescription = Option.empty,
              createdAt = now,
              updatedAt = now,
              deletedAt = Option.empty,
              createdUser = AuditUser.System,
              updatedUser = AuditUser.System,
              deletedUser = AuditUser.Empty,
              deleted = 0L,
              lockVersion = 0L
            ).toExternalAuthRequestDbRow
          )
        }
      }(using databaseExecutor)
      _ <- Future {
        info(
          "Spotify 認可リクエストを保存しました",
          kv("externalAuthRequestId", externalAuthRequestId),
          kv("scope", authorizationRequest.scopeText),
          kv("expiresAt", expiresAt.toString),
          kv("state", mask(authorizationRequest.state))
        )
      }(using defaultExecutor)
    } yield ()
  }
}
