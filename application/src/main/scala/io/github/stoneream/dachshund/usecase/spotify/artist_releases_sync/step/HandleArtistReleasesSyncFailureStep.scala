package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.{ArtistReleaseSyncQueueService, ArtistReleaseSyncQueueUpdateResult}
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.ArtistReleasesSyncResult.{Blocked, StaleLockSkipped, TemporaryFailure}
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.{ArtistReleasesSyncFailure, ArtistReleasesSyncFailureType, ArtistReleasesSyncResult}

import scala.concurrent.Future

@Singleton
private[artist_releases_sync] class HandleArtistReleasesSyncFailureStep @Inject() (
    applicationConfig: ApplicationConfig,
    queueService: ArtistReleaseSyncQueueService
) {
  def run(
      target: ArtistReleaseSyncQueueTarget,
      failure: ArtistReleasesSyncFailure,
      now: BusinessDateTime
  )(using DefaultExecutor): Future[ArtistReleasesSyncResult] =
    if (requiresOperationAction(failure.failureType)) {
      queueService
        .markBlocked(target, failure.failureType, now)
        .map(queueUpdateResult(_, Blocked))
    } else {
      val nextAttemptAt = CalculateNextArtistReleasesSyncAttemptAt(
        now = now,
        failureCount = target.attemptCount,
        failure = failure,
        retryConfig = applicationConfig.spotify.client.retry
      )
      queueService
        .markTemporaryFailure(target, failure.failureType, nextAttemptAt, now)
        .map(queueUpdateResult(_, TemporaryFailure(failure.failureType, nextAttemptAt)))
    }

  private def requiresOperationAction(failureType: String): Boolean =
    failureType == ArtistReleasesSyncFailureType.InvalidClientCredentials ||
      failureType == ArtistReleasesSyncFailureType.InsufficientScope

  private def queueUpdateResult(
      result: ArtistReleaseSyncQueueUpdateResult,
      updatedResult: ArtistReleasesSyncResult
  ): ArtistReleasesSyncResult =
    result match {
      case ArtistReleaseSyncQueueUpdateResult.Updated => updatedResult
      case ArtistReleaseSyncQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
    }
}
