package io.github.stoneream.dachshund.daemon.job

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.logging.Logger
import zio.ZIO

@Singleton
class JobSchedulerImpl @Inject() (
    jobLoader: JobLoader,
    jobRunner: JobRunner
) extends JobScheduler
    with Logger {
  override def run(): ZIO[Any, Throwable, Nothing] =
    for {
      jobs <- jobLoader.load()
      _ <- ZIO.succeed {
        logger.info(
          "scheduler を開始しました",
          kv("job.count", jobs.size)
        )
      }
      result <- ZIO.scoped {
        ZIO.foreachParDiscard(jobs)(job => jobRunner.run(job).forkScoped) *> ZIO.never
      }
    } yield result
}
