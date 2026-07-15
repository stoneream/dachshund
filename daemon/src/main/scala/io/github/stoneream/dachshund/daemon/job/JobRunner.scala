package io.github.stoneream.dachshund.daemon.job

import io.github.stoneream.dachshund.daemon.job.model.Job
import zio.ZIO

trait JobRunner {
  def run(job: Job): ZIO[Any, Throwable, Nothing]
}
