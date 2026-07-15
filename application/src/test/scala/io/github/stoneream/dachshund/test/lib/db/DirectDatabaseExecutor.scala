package io.github.stoneream.dachshund.test.lib.db

import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor

import scala.concurrent.ExecutionContextExecutor

private[lib] object DirectDatabaseExecutor extends ExecutionContextExecutor with DatabaseExecutor {
  override def execute(runnable: Runnable): Unit = runnable.run()

  override def reportFailure(cause: Throwable): Unit = throw cause
}
