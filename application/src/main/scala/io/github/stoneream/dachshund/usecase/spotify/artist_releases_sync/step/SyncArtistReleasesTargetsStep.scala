package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.ArtistReleasesSyncResult

import scala.concurrent.Future

@Singleton
private[artist_releases_sync] class SyncArtistReleasesTargetsStep @Inject() () extends TraceLogger {
  def run(
      targets: Seq[ArtistReleaseSyncQueueTarget]
  )(
      syncTarget: ArtistReleaseSyncQueueTarget => Future[ArtistReleasesSyncResult]
  )(using LoggingContext, DefaultExecutor): Future[Unit] =
    targets.foldLeft(Future.successful(())) { (futureDone, target) =>
      for {
        _ <- futureDone
        result <- syncTarget(target)
      } yield logProgress(target, result, targets.size)
    }

  private def logProgress(
      target: ArtistReleaseSyncQueueTarget,
      result: ArtistReleasesSyncResult,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "アーティストリリース同期を処理中です",
      kv("artistReleaseSyncQueueId", target.queueId),
      kv("artistReleasesSync.result", result),
      kv("artistReleasesSync.selectedCount", selectedCount)
    )
}
