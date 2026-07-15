package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.{FollowedArtistSyncQueueProgressResult, FollowedArtistSyncQueueService, FollowedArtistSyncQueueServiceException as QueueServiceException, FollowedArtistSyncQueueUpdateResult}
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyFollowedArtistsPage
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderException as TokenProviderException, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException as ClientException}
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.FollowedArtistsSyncResult.{PageProcessed, StaleLockSkipped}
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.{FollowedArtistsSyncFailure, FollowedArtistsSyncFailureType, FollowedArtistsSyncResult}
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.step.{HandleFollowedArtistsSyncFailureStep, SyncFollowedArtistsPageStep, SyncFollowedArtistsTargetsStep}

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class FollowedArtistsSyncUseCase @Inject() (
    queueService: FollowedArtistSyncQueueService,
    authorizationCodeAccessTokenProvider: SpotifyAuthorizationCodeAccessTokenProvider,
    spotifyClient: SpotifyClient,
    syncPageStep: SyncFollowedArtistsPageStep,
    handleFailureStep: HandleFollowedArtistsSyncFailureStep,
    syncTargetsStep: SyncFollowedArtistsTargetsStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      FollowedArtistsSyncUseCaseInput,
      FollowedArtistsSyncUseCaseOutput,
      FollowedArtistsSyncUseCaseException
    ]
    with TraceLogger {

  override def run(input: FollowedArtistsSyncUseCaseInput)(using LoggingContext): Future[FollowedArtistsSyncUseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    queueService
      .claimDueTargets(input.now, input.batchSize, input.processingLease)
      .flatMap { targets =>
        logTargetsSelected(input.batchSize, targets.size)
        syncTargetsStep
          .run(targets)(target => syncTarget(target, input.now))
          .map(_ => FollowedArtistsSyncUseCaseOutput())
          .recoverWith { case NonFatal(exception) =>
            failAfterReleasingTargets(targets, input.now, exception)
          }
      }
      .recoverWith { case NonFatal(exception) =>
        exception match {
          case QueueServiceException.TargetClaimFailed(queueId) =>
            Future.failed(FollowedArtistsSyncUseCaseException.TargetClaimFailed(queueId))
          case useCaseException: FollowedArtistsSyncUseCaseException =>
            Future.failed(useCaseException)
          case _ =>
            Future.failed(FollowedArtistsSyncUseCaseException.Unexpected(exception))
        }
      }
  }

  private def syncTarget(
      target: FollowedArtistSyncQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[FollowedArtistsSyncResult] =
    syncTargetPages(target, now).recoverWith {
      case failure: FollowedArtistsSyncFailure =>
        handleFailureStep.run(target, failure, now)
      case NonFatal(exception) =>
        releaseTargetAfterUnexpectedFailure(target, now, exception)
    }

  private def syncTargetPages(
      target: FollowedArtistSyncQueueTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[FollowedArtistsSyncResult] =
    fetchPage(target, now, forceRefresh = false, retryUnauthorized = true)
      .flatMap { page =>
        for {
          pageSyncResult <- syncPageStep.run(target, page, now)
          targetSyncResult <- page.nextAfterCursor match {
            case Some(nextAfterCursor) =>
              queueService.markPageProgressed(target, nextAfterCursor, now).flatMap {
                case FollowedArtistSyncQueueProgressResult.Updated(nextTarget) =>
                  logPageSynced(target, page, pageSyncResult.upsertedCount, pageSyncResult.deletedCount)
                  syncTarget(nextTarget, now)
                case FollowedArtistSyncQueueProgressResult.StaleLockSkipped =>
                  Future.successful(StaleLockSkipped)
              }
            case None =>
              queueService.markPageProcessed(target, page.nextAfterCursor, now).map { queueResult =>
                logPageSynced(target, page, pageSyncResult.upsertedCount, pageSyncResult.deletedCount)
                queueResult match {
                  case FollowedArtistSyncQueueUpdateResult.Updated => PageProcessed
                  case FollowedArtistSyncQueueUpdateResult.StaleLockSkipped => StaleLockSkipped
                }
              }
          }
        } yield targetSyncResult
      }

  private def fetchPage(
      target: FollowedArtistSyncQueueTarget,
      now: BusinessDateTime,
      forceRefresh: Boolean,
      retryUnauthorized: Boolean
  )(using LoggingContext, DefaultExecutor): Future[SpotifyFollowedArtistsPage] =
    authorizationCodeAccessTokenProvider
      .resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(target.userId, now, forceRefresh = forceRefresh))
      .recoverWith { case NonFatal(exception) =>
        Future.failed(
          exception match {
            case TokenProviderException.AuthorizationNotFound(_) =>
              FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.AuthorizationNotFound)
            case TokenProviderException.ReauthorizationRequired(_, reasonType, _) =>
              FollowedArtistsSyncFailure(reasonType)
            case TokenProviderException.TemporaryFailure(_, failureType, _, _) =>
              FollowedArtistsSyncFailure(failureType)
            case TokenProviderException.ConcurrentUpdate(_) =>
              FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.ConcurrentUpdate)
            case _ =>
              FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.Unknown)
          }
        )
      }
      .flatMap { accessToken =>
        spotifyClient
          .getFollowedArtists(
            accessToken = accessToken.accessToken,
            afterCursor = target.afterCursor,
            limit = target.requestedLimit
          )
          .recoverWith {
            case ClientException.Unauthorized(_) if retryUnauthorized =>
              fetchPage(target, now, forceRefresh = true, retryUnauthorized = false)
            case NonFatal(exception) =>
              Future.failed(
                exception match {
                  case ClientException.Unauthorized(_) =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.ClientError)
                  case ClientException.Forbidden(_) =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.InsufficientScope)
                  case ClientException.RateLimited(retryAfter, _) =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.RateLimited, retryAfter)
                  case ClientException.Network(_) =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.Network)
                  case ClientException.ServerError(_) =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.ServerError)
                  case ClientException.InvalidResponse(_) =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.InvalidResponse)
                  case ClientException.ClientError(_) =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.ClientError)
                  case _ =>
                    FollowedArtistsSyncFailure(FollowedArtistsSyncFailureType.Unknown)
                }
              )
          }
      }

  private def releaseTargetAfterUnexpectedFailure(
      target: FollowedArtistSyncQueueTarget,
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[FollowedArtistsSyncResult] =
    queueService
      .releaseProcessingTargets(Seq(target), now)
      .recoverWith { case NonFatal(releaseException) =>
        warn(
          "フォロー中アーティスト同期の target release に失敗しました",
          kv("followedArtistSyncQueueId", target.queueId),
          kv("failureClass", releaseException.getClass.getName),
          kv("originalFailureClass", exception.getClass.getName)
        )
        Future.successful(0)
      }
      .flatMap(_ => Future.failed[FollowedArtistsSyncResult](exception))

  private def failAfterReleasingTargets(
      targets: Seq[FollowedArtistSyncQueueTarget],
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[FollowedArtistsSyncUseCaseOutput] =
    queueService
      .releaseProcessingTargets(targets, now)
      .recoverWith { case NonFatal(releaseException) =>
        warn(
          "フォロー中アーティスト同期の abort 後 release に失敗しました",
          kv("failureClass", releaseException.getClass.getName),
          kv("originalFailureClass", exception.getClass.getName)
        )
        Future.successful(0)
      }
      .flatMap(_ => Future.failed[FollowedArtistsSyncUseCaseOutput](exception))

  private def logTargetsSelected(
      batchSize: Int,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "フォロー中アーティスト同期の対象を取得しました",
      kv("followedArtistsSync.batchSize", batchSize),
      kv("followedArtistsSync.selectedCount", selectedCount)
    )

  private def logPageSynced(
      target: FollowedArtistSyncQueueTarget,
      page: SpotifyFollowedArtistsPage,
      upsertedCount: Int,
      deletedCount: Int
  )(using LoggingContext): Unit =
    info(
      "フォロー中アーティスト同期ページを保存しました",
      kv("followedArtistSyncQueueId", target.queueId),
      kv("userId", target.userId),
      kv("followedArtistsSync.fetchedCount", page.artists.size),
      kv("followedArtistsSync.upsertedCount", upsertedCount),
      kv("followedArtistsSync.deletedCount", deletedCount),
      kv("followedArtistsSync.hasNextPage", page.nextAfterCursor.nonEmpty)
    )
}
