package io.github.stoneream.dachshund.daemon

import io.github.stoneream.dachshund.daemon.module.DaemonManagedExecutor
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor, IoDispatcher}

private[daemon] object DirectDaemonMainExecutor extends DefaultExecutor with DatabaseExecutor with IoDispatcher with DaemonManagedExecutor {
  override val name: String = "direct-daemon-main-test-executor"

  override def execute(runnable: Runnable): Unit = runnable.run()

  override def reportFailure(cause: Throwable): Unit = throw cause

  override def close(): Unit = ()
}
