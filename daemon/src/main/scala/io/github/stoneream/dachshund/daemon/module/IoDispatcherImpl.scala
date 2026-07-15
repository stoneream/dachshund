package io.github.stoneream.dachshund.daemon.module

import io.github.stoneream.dachshund.daemon.config.DaemonExecutorConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher

import java.util.concurrent.Executors

class IoDispatcherImpl(config: DaemonExecutorConfig)
    extends ExecutorServiceExecutionContext(
      name = "io-dispatcher",
      executorService = Executors.newFixedThreadPool(
        config.threadCount,
        new NamedDaemonThreadFactory("io-dispatcher")
      ),
      shutdownGracePeriod = config.shutdownGracePeriod
    )
    with IoDispatcher
