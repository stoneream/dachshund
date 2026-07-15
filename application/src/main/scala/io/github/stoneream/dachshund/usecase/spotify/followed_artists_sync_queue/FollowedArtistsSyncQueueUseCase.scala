package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync_queue

import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync_queue.{FollowedArtistsSyncQueueUseCaseException as UseCaseException, FollowedArtistsSyncQueueUseCaseInput as UseCaseInput, FollowedArtistsSyncQueueUseCaseOutput as UseCaseOutput}
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync_queue.step.CreateFollowedArtistSyncQueuesStep

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class FollowedArtistsSyncQueueUseCase @Inject() (
    createQueuesStep: CreateFollowedArtistSyncQueuesStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    createQueuesStep
      .run(
        now = input.now
      )
      .map { createdQueueCount =>
        logOutput(input, createdQueueCount)
        UseCaseOutput()
      }
  }.recoverWith { case NonFatal(exception) =>
    exception match {
      case useCaseException: UseCaseException =>
        Future.failed(useCaseException)
      case _ =>
        Future.failed(UseCaseException.Unexpected(exception))
    }
  }(using defaultExecutor)

  private def logOutput(
      input: UseCaseInput,
      createdQueueCount: Int
  )(using LoggingContext): Unit =
    info(
      "フォロー中アーティスト同期キューを作成しました",
      kv("followedArtistsSyncQueue.syncDate", input.now.toLocalDate),
      kv("followedArtistsSyncQueue.createdQueueCount", createdQueueCount)
    )
}
