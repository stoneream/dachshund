package io.github.stoneream.dachshund.daemon.job

import zio.ZIO

trait JobScheduler {
  def run(): ZIO[Any, Throwable, Nothing]
}
