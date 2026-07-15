package io.github.stoneream.dachshund.daemon.job

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.{JobName, JobSchedule}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.Logger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.{Clock, Schedule, UIO, ZIO}
import zio.duration2DurationOps

import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

@Singleton
class JobRunnerImpl @Inject() () extends JobRunner with Logger {
  override def run(job: Job): ZIO[Any, Throwable, Nothing] =
    (for {
      _ <- logJobStart(job)
      result <- runLoop(job)
    } yield result)
      .onInterrupt(logJobInterrupted(job.setting.name))
      .ensuring(logJobStopped(job.setting.name))

  private def runLoop(job: Job): ZIO[Any, Nothing, Nothing] =
    runOnce(job) *> (sleepUntilNextRun(job.setting.name, job.setting.schedule) *> runOnce(job)).forever

  private[job] def runOnce(job: Job): UIO[Unit] =
    for {
      attemptRef <- zio.Ref.make(1)
      _ <- runAttempt(job, attemptRef)
        .retryOrElse(
          retrySchedule(job, attemptRef),
          (exception, _) => logRetryExhausted(job.setting.name, job.setting.retryPolicy.maxAttempts, exception)
        )
    } yield ()

  private def runAttempt(job: Job, attemptRef: zio.Ref[Int]): ZIO[Any, Throwable, Unit] =
    ZIO.suspend {
      given LoggingContext = loggingContext(job.setting.name, "run")

      for {
        attempt <- attemptRef.get
        startedAtNanos <- ZIO.succeed(System.nanoTime())
        _ <- logRunStart(job.setting.name, attempt, job.setting.retryPolicy.maxAttempts)
        _ <- job
          .dispatch()
          .disconnect
          .timeoutFail(JobRunnerImpl.JobTimeoutException(job.setting.name))(zioDuration(job.setting.timeout))
          .tap(_ => logRunFinish(job.setting.name, attempt, startedAtNanos))
          .tapError(exception =>
            logRunFailure(
              name = job.setting.name,
              exception = exception,
              attempt = attempt,
              maxAttempts = job.setting.retryPolicy.maxAttempts,
              startedAtNanos = startedAtNanos
            )
          )
      } yield ()
    }

  private def sleepUntilNextRun(name: JobName, schedule: JobSchedule): UIO[Unit] =
    for {
      now <- Clock.instant.map(nowInstant => ZonedDateTime.ofInstant(nowInstant, ZoneId.systemDefault()))
      delay = schedule.nextDelay(now)
      _ <- logNextRun(name, delay)
      _ <- ZIO.sleep(zioDuration(delay))
    } yield ()

  private def logJobStart(job: Job): UIO[Unit] =
    ZIO.succeed {
      logger.info(
        s"job ${job.setting.name.value} を開始しました",
        kv("job.name", job.setting.name.value),
        kv("job.schedule", job.setting.schedule.toString),
        kv("job.timeout", job.setting.timeout.toString),
        kv("job.retry.maxAttempts", job.setting.retryPolicy.maxAttempts)
      )
    }

  private def logJobInterrupted(name: JobName): UIO[Unit] =
    ZIO.succeed {
      logger.info(
        s"job ${name.value} を中断しました",
        kv("job.name", name.value)
      )
    }

  private def logJobStopped(name: JobName): UIO[Unit] =
    ZIO.succeed {
      logger.info(
        s"job ${name.value} を停止しました",
        kv("job.name", name.value)
      )
    }

  private def logNextRun(name: JobName, delay: FiniteDuration): UIO[Unit] =
    ZIO.succeed {
      logger.info(
        s"job ${name.value} の次回実行を待機します",
        kv("job.name", name.value),
        kv("job.nextDelay", delay.toString)
      )
    }

  private def logRunStart(name: JobName, attempt: Int, maxAttempts: Int): UIO[Unit] =
    ZIO.succeed {
      logger.info(
        s"job ${name.value} の実行を開始しました",
        kv("job.name", name.value),
        kv("job.attempt", attempt),
        kv("job.maxAttempts", maxAttempts)
      )
    }

  private def logRunFinish(name: JobName, attempt: Int, startedAtNanos: Long): UIO[Unit] =
    ZIO.succeed {
      logger.info(
        s"job ${name.value} の実行が完了しました",
        kv("job.name", name.value),
        kv("job.attempt", attempt),
        kv("job.status", "succeeded"),
        kv("job.elapsedMillis", elapsedMillis(startedAtNanos))
      )
    }

  private def logRunFailure(
      name: JobName,
      exception: Throwable,
      attempt: Int,
      maxAttempts: Int,
      startedAtNanos: Long
  ): UIO[Unit] =
    ZIO.succeed {
      val message = exception match {
        case _: JobRunnerImpl.JobTimeoutException => s"job ${name.value} の実行がタイムアウトしました"
        case _ => s"job ${name.value} の実行に失敗しました"
      }

      logger.warn(
        message,
        kv("job.name", name.value),
        kv("job.attempt", attempt),
        kv("job.maxAttempts", maxAttempts),
        kv("job.status", failureStatus(exception)),
        kv("job.failureClass", exception.getClass.getName),
        kv("job.elapsedMillis", elapsedMillis(startedAtNanos))
      )
    }

  private def logRetry(name: JobName, nextAttempt: Int, delay: FiniteDuration): UIO[Unit] =
    ZIO.succeed {
      logger.info(
        s"job ${name.value} の実行を再試行します",
        kv("job.name", name.value),
        kv("job.nextAttempt", nextAttempt),
        kv("job.retryDelay", delay.toString)
      )
    }

  private def logRetryExhausted(name: JobName, maxAttempts: Int, exception: Throwable): UIO[Unit] =
    ZIO.succeed {
      logger.warn(
        s"job ${name.value} の再試行回数が上限に達しました",
        kv("job.name", name.value),
        kv("job.maxAttempts", maxAttempts),
        kv("job.failureClass", exception.getClass.getName)
      )
    }

  private def retrySchedule(job: Job, attemptRef: zio.Ref[Int]): Schedule[Any, Throwable, zio.Duration] = {
    val policy = job.setting.retryPolicy
    val baseSchedule =
      Schedule
        .exponential(zioDuration(policy.baseDelay))
        .modifyDelay((_, delay) => delay.min(zioDuration(policy.effectiveMaxDelay)))

    val jitteredSchedule =
      policy.jitterRatio match {
        case Some(ratio) if ratio > 0.0 =>
          baseSchedule.jittered(math.max(0.0, 1.0 - ratio), 1.0 + ratio)
        case _ =>
          baseSchedule
      }

    (jitteredSchedule && Schedule.recurs(policy.maxAttempts - 1))
      .onDecision {
        case (_, (delay, _), Schedule.Decision.Continue(_)) =>
          for {
            nextAttempt <- attemptRef.updateAndGet(_ + 1)
            _ <- logRetry(job.setting.name, nextAttempt, delay.asFiniteDuration)
          } yield ()
        case _ =>
          ZIO.unit
      }
      .map(_._1)
  }

  private def failureStatus(exception: Throwable): String =
    exception match {
      case _: JobRunnerImpl.JobTimeoutException => "timed_out"
      case _ => "failed"
    }

  private def loggingContext(name: JobName, phase: String): LoggingContext =
    LoggingContext(s"job-${name.value}-$phase-${UUID.randomUUID().toString}")

  private def zioDuration(duration: FiniteDuration): zio.Duration =
    zio.Duration.fromNanos(duration.toNanos)

  private def elapsedMillis(startedAtNanos: Long): Long =
    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
}

object JobRunnerImpl {
  final case class JobTimeoutException(jobName: JobName) extends RuntimeException(s"job timed out: ${jobName.value}")
}
