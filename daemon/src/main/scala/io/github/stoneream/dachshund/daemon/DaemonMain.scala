package io.github.stoneream.dachshund.daemon

import com.google.inject.{Guice, Inject, Singleton}
import io.github.stoneream.dachshund.daemon.job.JobScheduler
import io.github.stoneream.dachshund.daemon.lib.DaemonDatabaseInitializer
import io.github.stoneream.dachshund.daemon.module.{DaemonLifecycle, DaemonModule}
import io.github.stoneream.dachshund.logging.Logger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.daemon.ready.{DaemonReadyUseCase, DaemonReadyUseCaseInput}
import zio.{ExitCode, Scope, UIO, ZIO, ZIOAppDefault}

object DaemonMain extends ZIOAppDefault with Logger {
  override def run: ZIO[Scope, Nothing, Nothing] = {
    daemonApplication
      .catchAll(exitWithFailure)
  }

  private def daemonApplication: ZIO[Scope, Throwable, Nothing] = {
    for {
      main <- daemonMain
      result <- main.run
    } yield result
  }

  private def daemonMain: ZIO[Scope, Throwable, DaemonMain] =
    ZIO.acquireRelease(
      ZIO.attempt(
        Guice
          .createInjector(new DaemonModule())
          .getInstance(classOf[DaemonMain])
      )
    )(_.close())

  private def exitWithFailure(exception: Throwable): ZIO[Any, Nothing, Nothing] =
    ZIO.succeed {
      logger.error(
        "デーモンの実行に失敗しました",
        kv("daemon.failureClass", exception.getClass.getName)
      )
    } *> exit(ExitCode.failure) *> ZIO.never
}

@Singleton
final class DaemonMain @Inject() (
    initializer: DaemonDatabaseInitializer,
    daemonReadyUseCase: DaemonReadyUseCase,
    scheduler: JobScheduler,
    lifecycle: DaemonLifecycle
) extends Logger {
  def run: ZIO[Scope, Throwable, Nothing] =
    for {
      _ <- initializer.scoped
      _ <- runDaemonReady()
      _ <- ZIO.succeed {
        logger.info("デーモンを起動しました")
      }
      result <- scheduler.run()
    } yield result

  private def runDaemonReady(): ZIO[Any, Throwable, Unit] =
    ZIO.fromFuture { _ =>
      given LoggingContext = LoggingContext("daemon-ready")

      daemonReadyUseCase.run(DaemonReadyUseCaseInput())
    }.unit

  def close(): UIO[Unit] =
    lifecycle.close()
}
