package io.github.stoneream.dachshund.daemon.module

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor, IoDispatcher}
import io.github.stoneream.dachshund.logging.Logger
import zio.{UIO, ZIO}

@Singleton
class DaemonLifecycle @Inject() (
    defaultExecutor: DefaultExecutor,
    databaseExecutor: DatabaseExecutor,
    ioDispatcher: IoDispatcher
) extends Logger {
  private val executors: Seq[DaemonManagedExecutor] =
    Seq(defaultExecutor, databaseExecutor, ioDispatcher).map(managedExecutor)

  def close(): UIO[Unit] =
    ZIO.foreachDiscard(executors)(closeExecutor)

  private def closeExecutor(executor: DaemonManagedExecutor): UIO[Unit] =
    ZIO.attemptBlocking(executor.close()).catchAll { exception =>
      ZIO.succeed {
        logger.warn(
          "daemon executor の shutdown に失敗しました",
          kv("executor.name", executor.name),
          kv("executorShutdown.failureClass", exception.getClass.getName)
        )
      }
    }

  private def managedExecutor(executor: AnyRef): DaemonManagedExecutor =
    executor match {
      case managedExecutor: DaemonManagedExecutor => managedExecutor
      case _ => throw new IllegalStateException(s"daemon executor is not managed: ${executor.getClass.getName}")
    }
}
