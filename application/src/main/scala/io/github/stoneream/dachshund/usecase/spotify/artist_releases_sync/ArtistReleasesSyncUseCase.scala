package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.{ArtistReleaseSyncQueueService, ArtistReleaseSyncQueueUpdateResult, ArtistReleaseSyncQueueServiceException as QueueServiceException}
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyArtistReleaseSummaryPage}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException as ClientException}
import io.github.stoneream.dachshund.service.spotify.client_credentials.SpotifyClientCredentialsAccessTokenProvider
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.ArtistReleasesSyncResult.{PageProcessed, StaleLockSkipped}
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.{ArtistReleasesSyncFailure, ArtistReleasesSyncFailureType, ArtistReleasesSyncResult}
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.step.{FindUnsyncedArtistReleaseSummariesStep, HandleArtistReleasesSyncFailureStep, SyncArtistReleasePageStep, SyncArtistReleasesTargetsStep}

import java.io.IOException
import java.net.SocketTimeoutException
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class ArtistReleasesSyncUseCase @Inject() (
    queueService: ArtistReleaseSyncQueueService,
    clientCredentialsAccessTokenProvider: SpotifyClientCredentialsAccessTokenProvider,
    spotifyClient: SpotifyClient,
    findUnsyncedSummariesStep: FindUnsyncedArtistReleaseSummariesStep,
    syncPageStep: SyncArtistReleasePageStep,
    handleFailureStep: HandleArtistReleasesSyncFailureStep,
    syncTargetsStep: SyncArtistReleasesTargetsStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      ArtistReleasesSyncUseCaseInput,
      ArtistReleasesSyncUseCaseOutput,
      ArtistReleasesSyncUseCaseException
    ]
    with TraceLogger {

  override def run(input: ArtistReleasesSyncUseCaseInput)(using LoggingContext): Future[ArtistReleasesSyncUseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    queueService
      .claimDueTargets(input.now, input.batchSize, input.processingLease)
      .flatMap { targets =>
        logTargetsSelected(input.batchSize, targets.size)
        syncTargetsStep
          .run(targets, input.now)(target => syncTarget(target, input.now))
          .map(_ => ArtistReleasesSyncUseCaseOutput())
          .recoverWith { case NonFatal(exception) =>
            failAfterReleasingTargets(targets, input.now, exception)
          }
      }
      .recoverWith { case NonFatal(exception) =>
        exception match {
          case QueueServiceException.TargetClaimFailed(queueId) =>
            Future.failed(ArtistReleasesSyncUseCaseException.TargetClaimFailed(queueId))
          case useCaseException: ArtistReleasesSyncUseCaseException =>
            Future.failed(useCaseException)
          case _ =>
            Future.failed(ArtistReleasesSyncUseCaseException.Unexpected(exception))
        }
      }
  }

  private def syncTarget(
      target: ArtistReleaseSyncQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[ArtistReleasesSyncResult] =
    fetchSummaryPage(target, now)
      .flatMap { page =>
        for {
          unsyncedSummaries <- findUnsyncedSummariesStep.run(page.releases)
          releases <- fetchReleaseDetails(target, unsyncedSummaries, now)
          syncResult <- syncPageStep.run(releases, now)
          queueResult <- queueService.markPageProcessed(
            target = target,
            nextOffset = page.nextOffset.getOrElse(0),
            completed = page.nextOffset.isEmpty,
            now = now
          )
        } yield {
          logPageSynced(
            target = target,
            page = page,
            detailFetchCount = unsyncedSummaries.size,
            releaseCount = syncResult.releaseCount,
            trackCount = syncResult.trackCount
          )
          queueResult match {
            case ArtistReleaseSyncQueueUpdateResult.Updated => PageProcessed
            case ArtistReleaseSyncQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
          }
        }
      }
      .recoverWith {
        case failure: ArtistReleasesSyncFailure =>
          handleFailureStep.run(target, failure, now)
        case NonFatal(exception) =>
          handleUnexpectedTargetFailure(target, now, exception)
      }

  private def fetchSummaryPage(
      target: ArtistReleaseSyncQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[SpotifyArtistReleaseSummaryPage] =
    requestSpotify(now) { accessToken =>
      spotifyClient.getArtistReleaseSummaryPage(
        accessToken = accessToken,
        spotifyArtistCode = target.spotifyArtistCode,
        includeGroups = target.includeGroups,
        market = target.market,
        limit = target.requestedLimit,
        offset = target.nextOffset
      )
    }

  private def fetchReleaseDetails(
      target: ArtistReleaseSyncQueueTarget,
      summaries: Seq[SpotifyArtistReleaseSummary],
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[Seq[SpotifyArtistRelease]] =
    summaries.foldLeft(Future.successful(Vector.empty[SpotifyArtistRelease])) { (futureReleases, summary) =>
      for {
        releases <- futureReleases
        release <- requestSpotify(now) { accessToken =>
          spotifyClient.getArtistRelease(
            accessToken = accessToken,
            sourceSpotifyArtistCode = target.spotifyArtistCode,
            summary = summary,
            market = target.market
          )
        }
      } yield releases :+ release
    }

  private def requestSpotify[A](
      now: BusinessDateTime,
      forceRefresh: Boolean = false,
      retryUnauthorized: Boolean = true
  )(
      request: String => Future[A]
  )(using LoggingContext, DefaultExecutor): Future[A] =
    clientCredentialsAccessTokenProvider
      .resolve(now, forceRefresh = forceRefresh)
      .recoverWith { case NonFatal(exception) =>
        Future.failed(classifyFailure(exception))
      }
      .flatMap { accessToken =>
        request(accessToken.accessToken)
          .recoverWith {
            case ClientException.Unauthorized(_) if retryUnauthorized =>
              requestSpotify(now, forceRefresh = true, retryUnauthorized = false)(request)
            case NonFatal(exception) =>
              Future.failed(classifyFailure(exception))
          }
      }

  private def classifyFailure(exception: Throwable): ArtistReleasesSyncFailure =
    exception match {
      case e: SpotifyOAuthClientException =>
        val failureType = e.errorCode.map(_.trim).filter(_.nonEmpty) match {
          case Some("invalid_client") => ArtistReleasesSyncFailureType.InvalidClientCredentials
          case Some("invalid_response") => ArtistReleasesSyncFailureType.InvalidResponse
          case Some(_) if e.statusCode == 429 => ArtistReleasesSyncFailureType.RateLimited
          case Some(_) if e.statusCode >= 500 => ArtistReleasesSyncFailureType.ServerError
          case Some(_) => ArtistReleasesSyncFailureType.ClientError
          case None if e.statusCode == 429 => ArtistReleasesSyncFailureType.RateLimited
          case None if e.statusCode >= 500 => ArtistReleasesSyncFailureType.ServerError
          case None => ArtistReleasesSyncFailureType.ClientError
        }
        ArtistReleasesSyncFailure(failureType = failureType, retryAfter = e.error.retryAfter)
      case ClientException.Unauthorized(_) =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.InvalidClientCredentials)
      case ClientException.Forbidden(_) =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.InsufficientScope)
      case ClientException.RateLimited(retryAfter, _) =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.RateLimited, retryAfter)
      case ClientException.Network(_) =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.Network)
      case ClientException.ServerError(_) =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.ServerError)
      case ClientException.InvalidResponse(_) =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.InvalidResponse)
      case ClientException.ClientError(_) =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.ClientError)
      case _: SocketTimeoutException =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.Network)
      case _: IOException =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.Network)
      case _ =>
        ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.Unknown)
    }

  private def failAfterReleasingTargets(
      targets: Seq[ArtistReleaseSyncQueueTarget],
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[ArtistReleasesSyncUseCaseOutput] =
    queueService
      .releaseProcessingTargets(targets, now)
      .recoverWith { case NonFatal(releaseException) =>
        warn(
          "アーティストリリース同期の abort 後 release に失敗しました",
          kv("failureClass", releaseException.getClass.getName),
          kv("originalFailureClass", exception.getClass.getName)
        )
        Future.successful(0)
      }
      .flatMap(_ => Future.failed[ArtistReleasesSyncUseCaseOutput](exception))

  private def handleUnexpectedTargetFailure(
      target: ArtistReleaseSyncQueueTarget,
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[ArtistReleasesSyncResult] = {
    warn(
      "アーティストリリース同期 target の想定外失敗を一時失敗として記録します",
      kv("artistReleaseSyncQueueId", target.queueId),
      kv("spotifyArtistCode", target.spotifyArtistCode),
      kv("failureClass", exception.getClass.getName),
      kv("failureMessage", Option(exception.getMessage).getOrElse(""))
    )
    handleFailureStep.run(target, ArtistReleasesSyncFailure(ArtistReleasesSyncFailureType.Unknown), now)
  }

  private def logTargetsSelected(
      batchSize: Int,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "アーティストリリース同期の対象を取得しました",
      kv("artistReleasesSync.batchSize", batchSize),
      kv("artistReleasesSync.selectedCount", selectedCount)
    )

  private def logPageSynced(
      target: ArtistReleaseSyncQueueTarget,
      page: SpotifyArtistReleaseSummaryPage,
      detailFetchCount: Int,
      releaseCount: Int,
      trackCount: Int
  )(using LoggingContext): Unit =
    info(
      "アーティストリリース同期ページを保存しました",
      kv("artistReleaseSyncQueueId", target.queueId),
      kv("artistReleasesSync.offset", target.nextOffset),
      kv("artistReleasesSync.listedReleaseCount", page.releases.size),
      kv("artistReleasesSync.skippedExistingReleaseCount", page.releases.size - detailFetchCount),
      kv("artistReleasesSync.detailFetchCount", detailFetchCount),
      kv("artistReleasesSync.savedReleaseCount", releaseCount),
      kv("artistReleasesSync.savedTrackCount", trackCount),
      kv("artistReleasesSync.hasNextPage", page.nextOffset.nonEmpty)
    )
}
