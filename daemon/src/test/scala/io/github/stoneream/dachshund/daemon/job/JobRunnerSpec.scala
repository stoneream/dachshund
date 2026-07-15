package io.github.stoneream.dachshund.daemon.job

import io.github.stoneream.dachshund.daemon.config.{JobName, JobRetryPolicy, JobSchedule, JobSetting}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import nl.altindag.log.LogCaptor
import org.scalatest.featurespec.AnyFeatureSpec
import zio.{Exit, Runtime, Task, Unsafe, ZIO}

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.Promise as ScalaPromise
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class JobRunnerSpec extends AnyFeatureSpec {
  Feature("job runner") {
    Scenario("handler が成功した場合は runOnce が完了する") {
      val handler = new CapturingHandler(ZIO.unit)
      val runner = new JobRunnerImpl()

      unsafeRun(runner.runOnce(job(handler)))

      assert(handler.handleCount == 1)
    }

    Scenario("handler が成功した場合は完了ログ本文に job 名を含め成功状態と経過時間を出す") {
      val logCaptor = LogCaptor.forClass(classOf[JobRunnerImpl])
      logCaptor.disableConsoleOutput()
      try {
        val handler = new CapturingHandler(ZIO.unit)
        val runner = new JobRunnerImpl()

        unsafeRun(runner.runOnce(job(handler)))

        val arguments = capturedArguments(logCaptor, "job test-job の実行が完了しました")
        assert(arguments.contains("job.name=test-job"))
        assert(arguments.contains("job.attempt=1"))
        assert(arguments.contains("job.status=succeeded"))
        assert(arguments.exists(_.startsWith("job.elapsedMillis=")))
      } finally {
        logCaptor.close()
      }
    }

    Scenario("handler が失敗した場合も runOnce は失敗を外へ返さない") {
      val handler = new CapturingHandler(ZIO.fail(new RuntimeException("failed")))
      val runner = new JobRunnerImpl()

      unsafeRun(runner.runOnce(job(handler)))

      assert(handler.handleCount == 1)
    }

    Scenario("handler が失敗した場合は失敗ログ本文に job 名を含め失敗状態と経過時間を出す") {
      val logCaptor = LogCaptor.forClass(classOf[JobRunnerImpl])
      logCaptor.disableConsoleOutput()
      try {
        val handler = new CapturingHandler(ZIO.fail(new RuntimeException("failed")))
        val runner = new JobRunnerImpl()

        unsafeRun(runner.runOnce(job(handler)))

        val arguments = capturedArguments(logCaptor, "job test-job の実行に失敗しました")
        assert(arguments.contains("job.name=test-job"))
        assert(arguments.contains("job.attempt=1"))
        assert(arguments.contains("job.maxAttempts=1"))
        assert(arguments.contains("job.status=failed"))
        assert(arguments.exists(_.startsWith("job.failureClass=")))
        assert(arguments.exists(_.startsWith("job.elapsedMillis=")))
      } finally {
        logCaptor.close()
      }
    }

    Scenario("handler 失敗後も次回実行に進める") {
      val handler = new CapturingHandler(ZIO.fail(new RuntimeException("failed")))
      val runner = new JobRunnerImpl()

      unsafeRun(runner.runOnce(job(handler)))
      unsafeRun(runner.runOnce(job(handler)))

      assert(handler.handleCount == 2)
    }

    Scenario("handler が一時的に失敗した場合は bounded retry して成功できる") {
      val handler = new FailsThenSucceedsHandler(failureCount = 2)
      val runner = new JobRunnerImpl()

      unsafeRun(
        runner.runOnce(
          job(
            handler,
            retryPolicy = jobRetryPolicy(maxAttempts = 3)
          )
        )
      )

      assert(handler.handleCount == 3)
    }

    Scenario("handler が失敗し続ける場合は max attempts で打ち切る") {
      val handler = new CapturingHandler(ZIO.fail(new RuntimeException("failed")))
      val runner = new JobRunnerImpl()

      unsafeRun(
        runner.runOnce(
          job(
            handler,
            retryPolicy = jobRetryPolicy(maxAttempts = 3)
          )
        )
      )

      assert(handler.handleCount == 3)
    }

    Scenario("run は初回 schedule を待たずに handler を実行する") {
      val firstStarted = new CountDownLatch(1)
      val secondStarted = new CountDownLatch(1)
      val completion = ScalaPromise[Unit]()
      val handler = new FutureBackedHandler(completion, firstStarted, secondStarted)
      val runner = new JobRunnerImpl()

      val countAfterInitialRun = unsafeRun(
        (for {
          fiber <- runner.run(job(handler, schedule = 1.hour, timeout = 1.hour)).fork
          _ <- awaitLatch(firstStarted, "initial run")
          countAfterInitialRun <- ZIO.succeed(handler.handleCount)
          interrupted <- fiber.interrupt.timeout(zio.Duration.fromMillis(100))
          _ <- ZIO.attempt(assert(interrupted.nonEmpty, "job interruption did not complete"))
        } yield countAfterInitialRun).ensuring(ZIO.succeed(completion.trySuccess(())))
      )

      assert(countAfterInitialRun == 1)
    }

    Scenario("handler が timeout を超えた場合は未完了でも次回実行に進む") {
      val firstStarted = new CountDownLatch(1)
      val secondStarted = new CountDownLatch(1)
      val completion = ScalaPromise[Unit]()
      val handler = new FutureBackedHandler(completion, firstStarted, secondStarted)
      val runner = new JobRunnerImpl()

      val countAfterNextRun = unsafeRun(
        (for {
          fiber <- runner.run(job(handler, schedule = 1.millis, timeout = 1.millis)).fork
          _ <- awaitLatch(firstStarted, "first run")
          _ <- awaitLatch(secondStarted, "second run")
          countAfterNextRun <- ZIO.succeed(handler.handleCount)
          interrupted <- fiber.interrupt.timeout(zio.Duration.fromMillis(100))
          _ <- ZIO.attempt(assert(interrupted.nonEmpty, "job interruption did not complete"))
        } yield countAfterNextRun).ensuring(ZIO.succeed(completion.trySuccess(())))
      )

      assert(countAfterNextRun >= 2)
    }

    Scenario("handler が timeout を超えた場合は timeout ログ本文に job 名を含め失敗状態と経過時間を出す") {
      val logCaptor = LogCaptor.forClass(classOf[JobRunnerImpl])
      logCaptor.disableConsoleOutput()
      val firstStarted = new CountDownLatch(1)
      val secondStarted = new CountDownLatch(1)
      val completion = ScalaPromise[Unit]()
      val handler = new FutureBackedHandler(completion, firstStarted, secondStarted)
      val runner = new JobRunnerImpl()
      try {
        unsafeRun(
          runner
            .runOnce(job(handler, timeout = 1.millis))
            .ensuring(ZIO.succeed(completion.trySuccess(())))
        )

        val arguments = capturedArguments(logCaptor, "job test-job の実行がタイムアウトしました")
        assert(arguments.contains("job.name=test-job"))
        assert(arguments.contains("job.attempt=1"))
        assert(arguments.contains("job.maxAttempts=1"))
        assert(arguments.contains("job.status=timed_out"))
        assert(arguments.exists(_.startsWith("job.failureClass=")))
        assert(arguments.exists(_.startsWith("job.elapsedMillis=")))
      } finally {
        completion.trySuccess(())
        logCaptor.close()
      }
    }

    Scenario("handler が timeout を超えた場合は未完了でも retry に進む") {
      val firstStarted = new CountDownLatch(1)
      val secondStarted = new CountDownLatch(1)
      val completion = ScalaPromise[Unit]()
      val handler = new FutureBackedHandler(completion, firstStarted, secondStarted)
      val runner = new JobRunnerImpl()

      val countAfterRetry = unsafeRun(
        (for {
          fiber <- runner
            .runOnce(
              job(
                handler,
                timeout = 1.millis,
                retryPolicy = jobRetryPolicy(maxAttempts = 2)
              )
            )
            .fork
          _ <- awaitLatch(firstStarted, "first run")
          _ <- awaitLatch(secondStarted, "second run")
          countAfterRetry <- ZIO.succeed(handler.handleCount)
          _ <- ZIO.succeed(completion.trySuccess(()))
          completed <- fiber.join.timeout(zio.Duration.fromMillis(500))
          _ <- ZIO.attempt(assert(completed.nonEmpty, "retry did not finish after timeout"))
        } yield countAfterRetry).ensuring(ZIO.succeed(completion.trySuccess(())))
      )

      assert(countAfterRetry == 2)
      assert(handler.handleCount == 2)
    }

    Scenario("handler 実行中に interrupt された場合は handler 完了を待たずに停止する") {
      val firstStarted = new CountDownLatch(1)
      val secondStarted = new CountDownLatch(1)
      val completion = ScalaPromise[Unit]()
      val handler = new FutureBackedHandler(completion, firstStarted, secondStarted)
      val runner = new JobRunnerImpl()

      val interrupted = unsafeRun(
        (for {
          fiber <- runner.run(job(handler, schedule = 1.millis, timeout = 1.second)).fork
          _ <- awaitLatch(firstStarted, "first run")
          interrupted <- fiber.interrupt.timeout(zio.Duration.fromMillis(100))
        } yield interrupted).ensuring(ZIO.succeed(completion.trySuccess(())))
      )

      assert(interrupted.nonEmpty)
      assert(handler.handleCount == 1)
    }

  }

  private class CapturingHandler(effect: Task[Unit]) extends JobHandler {
    private val handleCounter = new AtomicInteger(0)

    def handleCount: Int = handleCounter.get()

    override def handle()(using LoggingContext): Task[Unit] =
      ZIO.succeed {
        handleCounter.incrementAndGet()
        ()
      } *> effect
  }

  private class FailsThenSucceedsHandler(failureCount: Int) extends JobHandler {
    private val handleCounter = new AtomicInteger(0)

    def handleCount: Int = handleCounter.get()

    override def handle()(using LoggingContext): Task[Unit] =
      ZIO.succeed(handleCounter.incrementAndGet()).flatMap { count =>
        if (count <= failureCount) {
          ZIO.fail(new RuntimeException("temporary failure"))
        } else {
          ZIO.unit
        }
      }
  }

  private class FutureBackedHandler(
      completion: ScalaPromise[Unit],
      firstStarted: CountDownLatch,
      secondStarted: CountDownLatch
  ) extends JobHandler {
    private val handleCounter = new AtomicInteger(0)

    def handleCount: Int = handleCounter.get()

    override def handle()(using LoggingContext): Task[Unit] =
      ZIO.succeed {
        val count = handleCounter.incrementAndGet()
        if (count == 1) {
          firstStarted.countDown()
        }
        if (count == 2) {
          secondStarted.countDown()
        }
        ()
      } *> ZIO.fromFuture(_ => completion.future)
  }

  private def capturedArguments(logCaptor: LogCaptor, message: String): Set[String] = {
    val events = logCaptor.getLogEvents.asScala.filter(_.getFormattedMessage == message)
    assert(events.nonEmpty, s"log message was not captured: $message")
    events.last.getArguments.asScala.map(_.toString).toSet
  }

  private def awaitLatch(latch: CountDownLatch, label: String): Task[Unit] =
    ZIO.attemptBlocking(latch.await(500, TimeUnit.MILLISECONDS)).flatMap { completed =>
      ZIO.attempt(assert(completed, s"$label was not reached"))
    }

  private def job(
      handler: JobHandler,
      schedule: FiniteDuration = 1.second,
      timeout: FiniteDuration = 1.second,
      retryPolicy: JobRetryPolicy = jobRetryPolicy()
  ): Job =
    new Job {
      override val setting: JobSetting = JobSetting(
        name = JobName("test-job"),
        schedule = JobSchedule.Every(schedule),
        timeout = timeout,
        retryPolicy = retryPolicy
      )

      override def dispatch()(using LoggingContext): Task[Unit] =
        handler.handle()
    }

  private def jobRetryPolicy(maxAttempts: Int = 1): JobRetryPolicy =
    JobRetryPolicy(
      maxAttempts = maxAttempts,
      baseDelay = 0.seconds,
      maxDelay = 0.seconds,
      jitterRatio = None
    )

  private def unsafeRun[A](task: Task[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task) match {
        case Exit.Success(value) => value
        case Exit.Failure(cause) => throw cause.squash
      }
    }
}
