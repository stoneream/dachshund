package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyAuthorizationRefreshQueueWriter, SpotifyAuthorizationWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.{SpotifyAccessTokenRefreshResult, SpotifyAuthorizationRefreshTarget, SpotifyRefreshedTokens}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotify access token refresh の成功結果を token table と queue に永続化
 */
@Singleton
private[refresh] class HandleSpotifyRefreshSuccessStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    authorizationWriter: SpotifyAuthorizationWriter,
    refreshQueueWriter: SpotifyAuthorizationRefreshQueueWriter,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {
  def run(
      target: SpotifyAuthorizationRefreshTarget,
      refreshedTokens: SpotifyRefreshedTokens,
      now: BusinessDateTime
  )(using LoggingContext): Future[SpotifyAccessTokenRefreshResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val authorizationUpdated = authorizationWriter.update(
          authorizationId = target.authorizationId,
          userId = target.userId,
          expectedLockVersion = target.authorizationLockVersion,
          expectedDeleted = 0L,
          scopeText = refreshedTokens.scopeText,
          accessTokenCipher = refreshedTokens.encryptedAccessToken.cipherText,
          accessTokenNonce = refreshedTokens.encryptedAccessToken.nonce,
          accessTokenTag = refreshedTokens.encryptedAccessToken.tag,
          refreshTokenCipher = refreshedTokens.encryptedRefreshToken.cipherText,
          refreshTokenNonce = refreshedTokens.encryptedRefreshToken.nonce,
          refreshTokenTag = refreshedTokens.encryptedRefreshToken.tag,
          encryptionAlgorithm = refreshedTokens.encryptedAccessToken.algorithm,
          encryptionKeyVersion = refreshedTokens.encryptedAccessToken.keyVersion,
          tokenType = refreshedTokens.tokenType,
          accessTokenExpiresAt = refreshedTokens.accessTokenExpiresAt,
          refreshMarginSeconds = target.refreshMarginSeconds,
          lastAuthorizedAt = target.lastAuthorizedAt,
          lastRefreshedAt = Some(now),
          updatedAt = now,
          deletedAt = Option.empty,
          updatedUser = AuditUser.System,
          deletedUser = AuditUser.Empty,
          deleted = 0L,
          lockVersion = target.authorizationLockVersion + 1L
        )
        val refreshQueueUpdated = refreshQueueWriter.update(
          queueId = target.queueId,
          authorizationId = target.authorizationId,
          expectedStatus = target.queueStatus,
          expectedLockToken = target.lockToken,
          expectedQueueLockVersion = target.queueLockVersion,
          expectedDeleted = target.queueDeleted,
          status = QueueJobStatus.Scheduled,
          nextAttemptAt = Some(refreshedTokens.nextRefreshAttemptAt),
          attemptCount = 0,
          lastFailedAt = Option.empty,
          lastErrorType = "",
          lastAttemptedAt = Some(now),
          completedAt = Some(now),
          lockToken = "",
          lockedUntil = Option.empty,
          updatedAt = now,
          deletedAt = Option.empty,
          updatedUser = AuditUser.System,
          deletedUser = AuditUser.Empty,
          deleted = 0L,
          lockVersion = target.queueLockVersion + 1L
        )
        if (authorizationUpdated && refreshQueueUpdated) true
        else throw StaleRefreshPersistence
      }
    }(using databaseExecutor)
      .recover { case StaleRefreshPersistence =>
        false
      }(using defaultExecutor)
      .map {
        case true =>
          SpotifyAccessTokenRefreshResult.Refreshed
        case false =>
          info(
            "Spotify access token refresh の成功保存をスキップしました",
            kv("spotifyAuthorizationId", target.authorizationId),
            kv("spotifyAuthorizationRefreshQueueId", target.queueId),
            kv("userId", target.userId),
            kv("reason", "stale_lock_version")
          )
          SpotifyAccessTokenRefreshResult.StaleLockSkipped
      }(using defaultExecutor)
}

private object StaleRefreshPersistence extends RuntimeException("stale refresh persistence")
