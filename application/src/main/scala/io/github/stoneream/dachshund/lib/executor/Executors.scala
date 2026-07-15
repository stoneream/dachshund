package io.github.stoneream.dachshund.lib.executor

import scala.concurrent.ExecutionContextExecutor

object Executors {

  trait DefaultExecutor extends ExecutionContextExecutor

  trait DatabaseExecutor extends ExecutionContextExecutor

  trait IoDispatcher extends ExecutionContextExecutor
}
