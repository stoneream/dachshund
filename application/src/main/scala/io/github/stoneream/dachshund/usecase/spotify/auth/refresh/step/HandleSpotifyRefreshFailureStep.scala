package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.SpotifyAuthorizationRefreshQueueWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.{SpotifyAccessTokenRefreshResult, SpotifyAuthorizationRefreshTarget, SpotifyRefreshFailure}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotify access token refresh の失敗結果を永続化
 */
@Singleton
private[refresh] class HandleSpotifyRefreshFailureStep @Inject() (
    applicationConfig: ApplicationConfig,
    databaseTransaction: DatabaseTransaction,
    refreshQueueWriter: SpotifyAuthorizationRefreshQueueWriter,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {
  def run(
      target: SpotifyAuthorizationRefreshTarget,
      failure: SpotifyRefreshFailure,
      now: BusinessDateTime
  )(using LoggingContext): Future[SpotifyAccessTokenRefreshResult] =
    if (SpotifyRefreshFailureClassifier.requiresReauthorization(failure.failureType)) {
      markReauthorizationRequired(target, failure.failureType, now)
    } else {
      markTemporaryFailure(target, failure, now)
    }

  private def markReauthorizationRequired(
      target: SpotifyAuthorizationRefreshTarget,
      reasonType: String,
      now: BusinessDateTime
  )(using LoggingContext): Future[SpotifyAccessTokenRefreshResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        refreshQueueWriter.update(
          queueId = target.queueId,
          authorizationId = target.authorizationId,
          expectedStatus = target.queueStatus,
          expectedLockToken = target.lockToken,
          expectedQueueLockVersion = target.queueLockVersion,
          expectedDeleted = target.queueDeleted,
          status = QueueJobStatus.Blocked,
          nextAttemptAt = Option.empty,
          attemptCount = target.attemptCount,
          lastFailedAt = Some(now),
          lastErrorType = reasonType,
          lastAttemptedAt = Some(now),
          completedAt = target.completedAt,
          lockToken = "",
          lockedUntil = Option.empty,
          updatedAt = now,
          deletedAt = Option.empty,
          updatedUser = AuditUser.System,
          deletedUser = AuditUser.Empty,
          deleted = 0L,
          lockVersion = target.queueLockVersion + 1L
        )
      }
    }(using databaseExecutor).map {
      case true =>
        error(
          "Spotify access token refresh は再サインインが必要な状態に分類されました",
          kv("spotifyAuthorizationId", target.authorizationId),
          kv("spotifyAuthorizationRefreshQueueId", target.queueId),
          kv("userId", target.userId),
          kv("failureType", reasonType),
          kv("requiresUserSignin", true)
        )
        SpotifyAccessTokenRefreshResult.ReauthorizationRequired
      case false =>
        info(
          "Spotify access token refresh の再認可状態更新をスキップしました",
          kv("spotifyAuthorizationId", target.authorizationId),
          kv("spotifyAuthorizationRefreshQueueId", target.queueId),
          kv("userId", target.userId),
          kv("reason", "stale_lock_version")
        )
        SpotifyAccessTokenRefreshResult.StaleLockSkipped
    }(using defaultExecutor)

  private def markTemporaryFailure(
      target: SpotifyAuthorizationRefreshTarget,
      failure: SpotifyRefreshFailure,
      now: BusinessDateTime
  )(using LoggingContext): Future[SpotifyAccessTokenRefreshResult] =
    Future {
      val nextRefreshAttemptAt = CalculateNextRefreshAttemptAt(
        now = now,
        failureCount = target.attemptCount,
        failure = failure,
        retryConfig = applicationConfig.spotify.client.retry
      )
      val updated = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        refreshQueueWriter.update(
          queueId = target.queueId,
          authorizationId = target.authorizationId,
          expectedStatus = target.queueStatus,
          expectedLockToken = target.lockToken,
          expectedQueueLockVersion = target.queueLockVersion,
          expectedDeleted = target.queueDeleted,
          status = QueueJobStatus.Scheduled,
          nextAttemptAt = Some(nextRefreshAttemptAt),
          attemptCount = target.attemptCount,
          lastFailedAt = Some(now),
          lastErrorType = failure.failureType,
          lastAttemptedAt = Some(now),
          completedAt = target.completedAt,
          lockToken = "",
          lockedUntil = Option.empty,
          updatedAt = now,
          deletedAt = Option.empty,
          updatedUser = AuditUser.System,
          deletedUser = AuditUser.Empty,
          deleted = 0L,
          lockVersion = target.queueLockVersion + 1L
        )
      }

      (updated, nextRefreshAttemptAt)
    }(using databaseExecutor).map {
      case (true, nextRefreshAttemptAt) =>
        error(
          "Spotify access token refresh は一時失敗として保存されました",
          kv("spotifyAuthorizationId", target.authorizationId),
          kv("spotifyAuthorizationRefreshQueueId", target.queueId),
          kv("userId", target.userId),
          kv("failureType", failure.failureType),
          kv("nextRefreshAttemptAt", nextRefreshAttemptAt.toLocalDateTime.toString)
        )
        SpotifyAccessTokenRefreshResult.TemporaryFailure
      case (false, _) =>
        info(
          "Spotify access token refresh の一時失敗更新をスキップしました",
          kv("spotifyAuthorizationId", target.authorizationId),
          kv("spotifyAuthorizationRefreshQueueId", target.queueId),
          kv("userId", target.userId),
          kv("reason", "stale_lock_version")
        )
        SpotifyAccessTokenRefreshResult.StaleLockSkipped
    }(using defaultExecutor)
}
