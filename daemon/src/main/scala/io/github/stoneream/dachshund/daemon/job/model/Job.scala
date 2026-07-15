package io.github.stoneream.dachshund.daemon.job.model

import io.github.stoneream.dachshund.daemon.config.JobSetting
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

trait Job {
  def setting: JobSetting

  def dispatch()(using LoggingContext): Task[Unit]
}
