package io.github.stoneream.dachshund.daemon.module

import io.github.stoneream.dachshund.logging.Logger

import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

abstract class ExecutorServiceExecutionContext(
    override val name: String,
    executorService: ExecutorService,
    shutdownGracePeriod: FiniteDuration
) extends ExecutionContextExecutor
    with DaemonManagedExecutor
    with Logger {
  private val executionContext = ExecutionContext.fromExecutorService(executorService)

  override def execute(runnable: Runnable): Unit =
    executionContext.execute(runnable)

  override def reportFailure(cause: Throwable): Unit =
    executionContext.reportFailure(cause)

  override def close(): Unit = {
    logger.info(
      "daemon executor の shutdown を開始しました",
      kv("executor.name", name),
      kv("executor.shutdownGracePeriod", shutdownGracePeriod.toString)
    )

    executorService.shutdown()
    try {
      if (executorService.awaitTermination(shutdownGracePeriod.toNanos, TimeUnit.NANOSECONDS)) {
        logger.info(
          "daemon executor の shutdown が完了しました",
          kv("executor.name", name)
        )
      } else {
        val droppedTaskCount = executorService.shutdownNow().size()
        logger.warn(
          "daemon executor の shutdown grace period を超過したため強制終了しました",
          kv("executor.name", name),
          kv("executor.shutdownGracePeriod", shutdownGracePeriod.toString),
          kv("executor.droppedTaskCount", droppedTaskCount)
        )
      }
    } catch {
      case _: InterruptedException =>
        val droppedTaskCount = executorService.shutdownNow().size()
        logger.warn(
          "daemon executor の shutdown が interrupt されたため強制終了しました",
          kv("executor.name", name),
          kv("executor.droppedTaskCount", droppedTaskCount)
        )
        Thread.currentThread().interrupt()
    }
  }
}
