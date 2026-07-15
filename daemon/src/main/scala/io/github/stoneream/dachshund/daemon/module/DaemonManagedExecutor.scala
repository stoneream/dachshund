package io.github.stoneream.dachshund.daemon.module

trait DaemonManagedExecutor extends AutoCloseable {
  def name: String
}
