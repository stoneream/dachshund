package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.FollowedArtistSyncQueueService
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.{FollowedArtistsSyncFailureType, FollowedArtistsSyncResult}
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.FollowedArtistsSyncResult.TemporaryFailure

import scala.concurrent.Future

/**
 * claim 済みのフォロー中 artist 同期 target 群を順に処理する step。
 *
 * target ごとの同期処理は呼び出し元に委ね、順次実行、progress ログを担当する。
 */
@Singleton
private[followed_artists_sync] class SyncFollowedArtistsTargetsStep @Inject() (
    queueService: FollowedArtistSyncQueueService
) extends TraceLogger {
  def run(
      targets: Seq[FollowedArtistSyncQueueTarget],
      now: BusinessDateTime
  )(
      syncTarget: FollowedArtistSyncQueueTarget => Future[FollowedArtistsSyncResult]
  )(using LoggingContext, DefaultExecutor): Future[Unit] =
    syncNext(targets.toList, targets.size, now, syncTarget)

  private def syncNext(
      remainingTargets: List[FollowedArtistSyncQueueTarget],
      selectedCount: Int,
      now: BusinessDateTime,
      syncTarget: FollowedArtistSyncQueueTarget => Future[FollowedArtistsSyncResult]
  )(using LoggingContext, DefaultExecutor): Future[Unit] =
    remainingTargets match {
      case Nil =>
        Future.successful(())
      case target :: tail =>
        syncTarget(target).flatMap { result =>
          logProgress(target, result, selectedCount)
          result match {
            case TemporaryFailure(failureType, nextAttemptAt) if shouldStopBatch(failureType) =>
              deferRemainingTargets(tail, failureType, nextAttemptAt, now)
            case _ =>
              syncNext(tail, selectedCount, now, syncTarget)
          }
        }
    }

  private def deferRemainingTargets(
      targets: List[FollowedArtistSyncQueueTarget],
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[Unit] = {
    warn(
      "Spotify のリクエスト制限によりフォロー中アーティスト同期バッチの残りを延期します",
      kv("followedArtistsSync.failureType", failureType),
      kv("followedArtistsSync.deferredCount", targets.size),
      kv("followedArtistsSync.nextAttemptAt", nextAttemptAt)
    )
    targets.foldLeft(Future.successful(())) { (futureDone, target) =>
      futureDone.flatMap(_ => queueService.markTemporaryFailure(target, failureType, nextAttemptAt, now).map(_ => ()))
    }
  }

  private def shouldStopBatch(failureType: String): Boolean =
    failureType == FollowedArtistsSyncFailureType.RateLimited

  private def logProgress(
      target: FollowedArtistSyncQueueTarget,
      result: FollowedArtistsSyncResult,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "フォロー中アーティスト同期を処理中です",
      kv("followedArtistSyncQueueId", target.queueId),
      kv("userId", target.userId),
      kv("followedArtistsSync.result", result),
      kv("followedArtistsSync.selectedCount", selectedCount)
    )
}
