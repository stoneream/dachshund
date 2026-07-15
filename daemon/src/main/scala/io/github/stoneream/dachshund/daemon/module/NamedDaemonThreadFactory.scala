package io.github.stoneream.dachshund.daemon.module

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class NamedDaemonThreadFactory(name: String) extends ThreadFactory {
  private val nextThreadNumber = new AtomicInteger(1)

  override def newThread(runnable: Runnable): Thread = {
    val thread = new Thread(
      runnable,
      s"dachshund-daemon-$name-${nextThreadNumber.getAndIncrement()}"
    )
    thread.setDaemon(true)
    thread
  }
}
