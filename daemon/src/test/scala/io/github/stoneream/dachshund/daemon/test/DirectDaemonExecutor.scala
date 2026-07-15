package io.github.stoneream.dachshund.daemon.test

import io.github.stoneream.dachshund.daemon.module.DaemonManagedExecutor
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor, IoDispatcher}

private[test] object DirectDaemonExecutor extends DefaultExecutor with DatabaseExecutor with IoDispatcher with DaemonManagedExecutor {
  override val name: String = "direct-daemon-test-executor"

  override def execute(runnable: Runnable): Unit = runnable.run()

  override def reportFailure(cause: Throwable): Unit = throw cause

  override def close(): Unit = ()
}
