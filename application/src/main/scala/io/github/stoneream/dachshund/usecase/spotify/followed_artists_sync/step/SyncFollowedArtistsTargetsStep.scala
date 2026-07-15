package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.FollowedArtistsSyncResult

import scala.concurrent.Future

/**
 * claim 済みのフォロー中 artist 同期 target 群を順に処理する step。
 *
 * target ごとの同期処理は呼び出し元に委ね、順次実行、progress ログを担当する。
 */
@Singleton
private[followed_artists_sync] class SyncFollowedArtistsTargetsStep @Inject() () extends TraceLogger {
  def run(
      targets: Seq[FollowedArtistSyncQueueTarget]
  )(
      syncTarget: FollowedArtistSyncQueueTarget => Future[FollowedArtistsSyncResult]
  )(using LoggingContext, DefaultExecutor): Future[Unit] =
    targets.foldLeft(Future.successful(())) { (futureDone, target) =>
      for {
        _ <- futureDone
        result <- syncTarget(target)
      } yield logProgress(target, result, targets.size)
    }

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
