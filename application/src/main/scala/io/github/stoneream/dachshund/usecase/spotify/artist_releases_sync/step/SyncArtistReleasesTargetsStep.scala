package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.ArtistReleaseSyncQueueService
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.{ArtistReleasesSyncFailureType, ArtistReleasesSyncResult}
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.ArtistReleasesSyncResult.TemporaryFailure

import scala.concurrent.Future

@Singleton
private[artist_releases_sync] class SyncArtistReleasesTargetsStep @Inject() (
    queueService: ArtistReleaseSyncQueueService
) extends TraceLogger {
  def run(
      targets: Seq[ArtistReleaseSyncQueueTarget],
      now: BusinessDateTime
  )(
      syncTarget: ArtistReleaseSyncQueueTarget => Future[ArtistReleasesSyncResult]
  )(using LoggingContext, DefaultExecutor): Future[Unit] =
    syncNext(targets.toList, targets.size, now, syncTarget)

  private def syncNext(
      remainingTargets: List[ArtistReleaseSyncQueueTarget],
      selectedCount: Int,
      now: BusinessDateTime,
      syncTarget: ArtistReleaseSyncQueueTarget => Future[ArtistReleasesSyncResult]
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
      targets: List[ArtistReleaseSyncQueueTarget],
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[Unit] = {
    warn(
      "Spotify のリクエスト制限によりアーティストリリース同期バッチの残りを延期します",
      kv("artistReleasesSync.failureType", failureType),
      kv("artistReleasesSync.deferredCount", targets.size),
      kv("artistReleasesSync.nextAttemptAt", nextAttemptAt)
    )
    targets.foldLeft(Future.successful(())) { (futureDone, target) =>
      futureDone.flatMap(_ => queueService.markTemporaryFailure(target, failureType, nextAttemptAt, now).map(_ => ()))
    }
  }

  private def shouldStopBatch(failureType: String): Boolean =
    failureType == ArtistReleasesSyncFailureType.RateLimited

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
