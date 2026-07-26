package io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.step.{FindMissingUserNewReleaseEventsStep, WriteMissingUserNewReleaseEventsStep}

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class UserNewReleaseEventsSyncUseCase @Inject() (
    findMissingUserNewReleaseEventsStep: FindMissingUserNewReleaseEventsStep,
    writeMissingUserNewReleaseEventsStep: WriteMissingUserNewReleaseEventsStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UserNewReleaseEventsSyncUseCaseInput,
      UserNewReleaseEventsSyncUseCaseOutput,
      UserNewReleaseEventsSyncUseCaseException
    ]
    with TraceLogger {
  private val DetectionSyncCode = "user-new-release-events-sync"

  override def run(input: UserNewReleaseEventsSyncUseCaseInput)(using LoggingContext): Future[UserNewReleaseEventsSyncUseCaseOutput] = {
    findMissingUserNewReleaseEventsStep
      .run(input.now, input.batchSize)
      .flatMap { targets =>
        logMissingSelected(input.batchSize, targets.size)
        writeMissingUserNewReleaseEventsStep
          .run(
            targets = targets,
            detectionSyncCode = DetectionSyncCode,
            detectedAt = input.now
          )
          .map { result =>
            logOutput(
              input,
              selectedCount = targets.size,
              createdCount = result.createdCount,
              notificationQueueCreatedCount = result.notificationQueueCreatedCount
            )
            UserNewReleaseEventsSyncUseCaseOutput(createdCount = result.createdCount)
          }(using defaultExecutor)
      }(using defaultExecutor)
      .recoverWith { case NonFatal(exception) =>
        Future.failed(UserNewReleaseEventsSyncUseCaseException.Unexpected(exception))
      }(using defaultExecutor)
  }

  private def logMissingSelected(
      batchSize: Int,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "ユーザー別新着リリース履歴の作成対象を取得しました",
      kv("userNewReleaseEventsSync.batchSize", batchSize),
      kv("userNewReleaseEventsSync.selectedCount", selectedCount)
    )

  private def logOutput(
      input: UserNewReleaseEventsSyncUseCaseInput,
      selectedCount: Int,
      createdCount: Int,
      notificationQueueCreatedCount: Int
  )(using LoggingContext): Unit =
    info(
      "ユーザー別新着リリース履歴を作成しました",
      kv("userNewReleaseEventsSync.batchSize", input.batchSize),
      kv("userNewReleaseEventsSync.selectedCount", selectedCount),
      kv("userNewReleaseEventsSync.createdCount", createdCount),
      kv("userNewReleaseEventsSync.notificationQueueCreatedCount", notificationQueueCreatedCount),
      kv("userNewReleaseEventsSync.detectedAt", input.now.toLocalDateTime)
    )
}
