package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.{FollowedArtistSyncQueueService, FollowedArtistSyncQueueUpdateResult}
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.FollowedArtistsSyncResult.{Blocked, StaleLockSkipped, TemporaryFailure}
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.{FollowedArtistsSyncFailure, FollowedArtistsSyncFailureType, FollowedArtistsSyncResult}

import scala.concurrent.Future

/**
 * フォロー中 artist 同期で発生した失敗を queue 状態へ反映する step。
 *
 * ユーザー操作または運用対応が必要な失敗は BLOCKED にし、再試行可能な失敗は retry 時刻を計算して SCHEDULED に戻す。
 */
@Singleton
private[followed_artists_sync] class HandleFollowedArtistsSyncFailureStep @Inject() (
    applicationConfig: ApplicationConfig,
    queueService: FollowedArtistSyncQueueService
) {
  def run(
      target: FollowedArtistSyncQueueTarget,
      failure: FollowedArtistsSyncFailure,
      now: BusinessDateTime
  )(using DefaultExecutor): Future[FollowedArtistsSyncResult] =
    if (requiresUserAction(failure.failureType)) {
      queueService
        .markBlocked(target, failure.failureType, now)
        .map(queueUpdateResult(_, Blocked))
    } else {
      val nextAttemptAt = CalculateNextFollowedArtistsSyncAttemptAt(
        now = now,
        failureCount = target.attemptCount,
        failure = failure,
        retryConfig = applicationConfig.spotify.client.retry
      )
      queueService
        .markTemporaryFailure(target, failure.failureType, nextAttemptAt, now)
        .map(queueUpdateResult(_, TemporaryFailure(failure.failureType, nextAttemptAt)))
    }

  private def requiresUserAction(failureType: String): Boolean =
    failureType == FollowedArtistsSyncFailureType.AuthorizationNotFound ||
      failureType == FollowedArtistsSyncFailureType.InsufficientScope ||
      failureType == "invalid_grant" ||
      failureType == "token_decrypt_failed" ||
      failureType == "insufficient_scope"

  private def queueUpdateResult(
      result: FollowedArtistSyncQueueUpdateResult,
      updatedResult: FollowedArtistsSyncResult
  ): FollowedArtistsSyncResult =
    result match {
      case FollowedArtistSyncQueueUpdateResult.Updated => updatedResult
      case FollowedArtistSyncQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
    }
}
