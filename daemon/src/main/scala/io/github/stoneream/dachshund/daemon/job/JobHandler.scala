package io.github.stoneream.dachshund.daemon.job

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

trait JobHandler {
  def handle()(using LoggingContext): Task[Unit]
}
