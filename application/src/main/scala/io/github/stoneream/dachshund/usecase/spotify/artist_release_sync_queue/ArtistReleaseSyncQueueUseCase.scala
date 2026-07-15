package io.github.stoneream.dachshund.usecase.spotify.artist_release_sync_queue

import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.artist_release_sync_queue.{ArtistReleaseSyncQueueUseCaseException as UseCaseException, ArtistReleaseSyncQueueUseCaseInput as UseCaseInput, ArtistReleaseSyncQueueUseCaseOutput as UseCaseOutput}
import io.github.stoneream.dachshund.usecase.spotify.artist_release_sync_queue.step.CreateArtistReleaseSyncQueuesStep

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class ArtistReleaseSyncQueueUseCase @Inject() (
    createQueuesStep: CreateArtistReleaseSyncQueuesStep,
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
      .map { scheduledQueueCount =>
        logOutput(input, scheduledQueueCount)
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
      scheduledQueueCount: Int
  )(using LoggingContext): Unit =
    info(
      "アーティストリリース同期キューを作成しました",
      kv("artistReleaseSyncQueue.queuedAt", input.now.toLocalDateTime),
      kv("artistReleaseSyncQueue.scheduledQueueCount", scheduledQueueCount)
    )
}
