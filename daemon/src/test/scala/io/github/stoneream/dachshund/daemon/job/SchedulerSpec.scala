package io.github.stoneream.dachshund.daemon.job

import io.github.stoneream.dachshund.daemon.config.{JobName, JobRetryPolicy, JobSchedule, JobSetting}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import org.scalatest.featurespec.AnyFeatureSpec
import zio.{Exit, Runtime, Task, Unsafe, ZIO}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class SchedulerSpec extends AnyFeatureSpec {
  Feature("scheduler") {
    Scenario("loader が返した複数 job を並列起動する") {
      val first = job("first-job")
      val second = job("second-job")
      val runner = new CapturingJobRunner
      val scheduler = new JobSchedulerImpl(
        jobLoader = () => ZIO.succeed(List(first, second)),
        jobRunner = runner
      )

      unsafeRun(scheduler.run().timeout(zio.Duration.fromMillis(200)))

      assert(runner.startedJobNames == Set(JobName("first-job"), JobName("second-job")))
    }
  }

  private class CapturingJobRunner extends JobRunner {
    private val startedNames = new ConcurrentLinkedQueue[JobName]()

    def startedJobNames: Set[JobName] =
      startedNames.asScala.toSet

    override def run(job: Job): ZIO[Any, Throwable, Nothing] =
      ZIO.succeed {
        startedNames.add(job.setting.name)
      } *> ZIO.sleep(zio.Duration.fromMillis(50)) *> ZIO.never
  }

  private def job(name: String): Job =
    new Job {
      override val setting: JobSetting = JobSetting(
        name = JobName(name),
        enabled = true,
        schedule = JobSchedule.Every(1.second),
        timeout = 1.second,
        retryPolicy = jobRetryPolicy
      )

      override def dispatch()(using LoggingContext): Task[Unit] =
        ZIO.unit
    }

  private def jobRetryPolicy: JobRetryPolicy =
    JobRetryPolicy(
      maxAttempts = 1,
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
