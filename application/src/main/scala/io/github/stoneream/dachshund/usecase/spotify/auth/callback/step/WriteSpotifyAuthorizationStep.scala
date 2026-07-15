package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationRefreshQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{UserSpotifyAuthorizationRefreshQueueSource, UserSpotifyAuthorizationSource}
import io.github.stoneream.dachshund.infra.db.reader.auth.callback.SpotifyAuthorizationReader
import io.github.stoneream.dachshund.infra.db.reader.auth.callback.SpotifyAuthorizationReader.{AuthorizationRow, RefreshQueueRow}
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyAuthorizationRefreshQueueWriter, SpotifyAuthorizationWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.model.ExternalAuthRequest
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse as SpotifyTokenResponse
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.EncryptedSpotifyTokenPair

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/**
 * Spotify認可情報をユーザーに紐づけて永続化
 */
@Singleton
private[callback] class WriteSpotifyAuthorizationStep @Inject() (
    applicationConfig: ApplicationConfig,
    databaseTransaction: DatabaseTransaction,
    authorizationReader: SpotifyAuthorizationReader,
    authorizationWriter: SpotifyAuthorizationWriter,
    refreshQueueWriter: SpotifyAuthorizationRefreshQueueWriter,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) {
  def run(
      userId: Long,
      externalAuthRequest: ExternalAuthRequest,
      tokenResponse: SpotifyTokenResponse,
      encryptedTokens: EncryptedSpotifyTokenPair,
      now: BusinessDateTime
  ): Future[Long] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val scopeText = NormalizeSpotifyScopeText(tokenResponse.scope.getOrElse(externalAuthRequest.scopes))
        val accessTokenExpiresAt = now.plus(tokenResponse.expiresIn.seconds)
        val refreshMarginSeconds = applicationConfig.spotify.token.refreshMargin.toSeconds.toInt
        val auditUser = AuditUser.User(userId)

        val authorizationId =
          authorizationReader.findAuthorizationByUserId(userId) match {
            case Some(row) =>
              updateAuthorization(
                row = row,
                scopeText = scopeText,
                tokenResponse = tokenResponse,
                encryptedTokens = encryptedTokens,
                accessTokenExpiresAt = accessTokenExpiresAt,
                refreshMarginSeconds = refreshMarginSeconds,
                now = now,
                auditUser = auditUser
              )
              row.authorizationId
            case None =>
              authorizationWriter.write(
                UserSpotifyAuthorizationSource(
                  userId = userId,
                  scopeText = scopeText,
                  accessTokenCipher = encryptedTokens.accessToken.cipherText,
                  accessTokenNonce = encryptedTokens.accessToken.nonce,
                  accessTokenTag = encryptedTokens.accessToken.tag,
                  refreshTokenCipher = encryptedTokens.refreshToken.cipherText,
                  refreshTokenNonce = encryptedTokens.refreshToken.nonce,
                  refreshTokenTag = encryptedTokens.refreshToken.tag,
                  encryptionAlgorithm = encryptedTokens.accessToken.algorithm,
                  encryptionKeyVersion = encryptedTokens.accessToken.keyVersion,
                  tokenType = tokenResponse.tokenType,
                  accessTokenExpiresAt = accessTokenExpiresAt,
                  refreshMarginSeconds = refreshMarginSeconds,
                  lastAuthorizedAt = Some(now),
                  lastRefreshedAt = Some(now),
                  createdAt = now,
                  updatedAt = now,
                  deletedAt = Option.empty,
                  createdUser = auditUser,
                  updatedUser = auditUser,
                  deletedUser = AuditUser.Empty,
                  deleted = 0L,
                  lockVersion = 0L
                ).toUserSpotifyAuthorizationDbRow
              )
              authorizationReader
                .findAuthorizationByUserId(userId)
                .map(_.authorizationId)
                .getOrElse {
                  throw UseCaseException.AuthorizationPersistenceFailed(userId = Some(userId))
                }
          }

        val nextRefreshAttemptAt = accessTokenExpiresAt.minus(refreshMarginSeconds.seconds)
        authorizationReader.findRefreshQueueByAuthorizationId(authorizationId) match {
          case Some(row) =>
            updateRefreshQueue(
              row = row,
              nextRefreshAttemptAt = nextRefreshAttemptAt,
              now = now,
              auditUser = auditUser
            )
          case None =>
            refreshQueueWriter.write(
              UserSpotifyAuthorizationRefreshQueueSource(
                authorizationId = authorizationId,
                status = QueueJobStatus.Scheduled,
                nextAttemptAt = Some(nextRefreshAttemptAt),
                attemptCount = 0,
                lastFailedAt = Option.empty,
                lastErrorType = "",
                lockToken = "",
                lockedUntil = Option.empty,
                lastAttemptedAt = Option.empty,
                completedAt = Option.empty,
                createdAt = now,
                updatedAt = now,
                deletedAt = Option.empty,
                createdUser = auditUser,
                updatedUser = auditUser,
                deletedUser = AuditUser.Empty,
                deleted = 0L,
                lockVersion = 0L
              ).toUserSpotifyAuthorizationRefreshQueueDbRow
            )
        }

        authorizationId
      }
    }(using databaseExecutor).recoverWith {
      case e: UseCaseException => Future.failed(e)
      case NonFatal(e) => Future.failed(UseCaseException.AuthorizationPersistenceFailed(e))
    }(using defaultExecutor)

  private def updateAuthorization(
      row: AuthorizationRow,
      scopeText: String,
      tokenResponse: SpotifyTokenResponse,
      encryptedTokens: EncryptedSpotifyTokenPair,
      accessTokenExpiresAt: BusinessDateTime,
      refreshMarginSeconds: Int,
      now: BusinessDateTime,
      auditUser: AuditUser
  )(using scalikejdbc.DBSession): Unit = {
    authorizationWriter.update(
      authorizationId = row.authorizationId,
      userId = row.userId,
      expectedLockVersion = row.lockVersion,
      expectedDeleted = row.deleted,
      scopeText = scopeText,
      accessTokenCipher = encryptedTokens.accessToken.cipherText,
      accessTokenNonce = encryptedTokens.accessToken.nonce,
      accessTokenTag = encryptedTokens.accessToken.tag,
      refreshTokenCipher = encryptedTokens.refreshToken.cipherText,
      refreshTokenNonce = encryptedTokens.refreshToken.nonce,
      refreshTokenTag = encryptedTokens.refreshToken.tag,
      encryptionAlgorithm = encryptedTokens.accessToken.algorithm,
      encryptionKeyVersion = encryptedTokens.accessToken.keyVersion,
      tokenType = tokenResponse.tokenType,
      accessTokenExpiresAt = accessTokenExpiresAt,
      refreshMarginSeconds = refreshMarginSeconds,
      lastAuthorizedAt = Some(now),
      lastRefreshedAt = Some(now),
      updatedAt = now,
      deletedAt = Option.empty,
      updatedUser = auditUser,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = row.lockVersion + 1L
    )
  }

  private def updateRefreshQueue(
      row: RefreshQueueRow,
      nextRefreshAttemptAt: BusinessDateTime,
      now: BusinessDateTime,
      auditUser: AuditUser
  )(using scalikejdbc.DBSession): Unit = {
    refreshQueueWriter.update(
      queueId = row.queueId,
      authorizationId = row.authorizationId,
      expectedStatus = row.status,
      expectedLockToken = row.lockToken,
      expectedQueueLockVersion = row.lockVersion,
      expectedDeleted = row.deleted,
      status = QueueJobStatus.Scheduled,
      nextAttemptAt = Some(nextRefreshAttemptAt),
      attemptCount = 0,
      lastFailedAt = Option.empty,
      lastErrorType = "",
      lastAttemptedAt = Option.empty,
      completedAt = Option.empty,
      lockToken = "",
      lockedUntil = Option.empty,
      updatedAt = now,
      deletedAt = Option.empty,
      updatedUser = auditUser,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = row.lockVersion + 1L
    )
  }
}
