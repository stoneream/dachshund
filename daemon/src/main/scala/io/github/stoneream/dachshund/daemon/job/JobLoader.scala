package io.github.stoneream.dachshund.daemon.job

import io.github.stoneream.dachshund.daemon.job.model.Job
import zio.Task

trait JobLoader {
  def load(): Task[List[Job]]
}
