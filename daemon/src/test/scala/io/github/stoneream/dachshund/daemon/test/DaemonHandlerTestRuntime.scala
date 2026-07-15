package io.github.stoneream.dachshund.daemon.test

import zio.{Exit, Runtime, Task, Unsafe}

private[test] trait DaemonHandlerTestRuntime {
  protected def unsafeRun[A](task: Task[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task) match {
        case Exit.Success(value) => value
        case Exit.Failure(cause) => throw cause.squash
      }
    }
}
