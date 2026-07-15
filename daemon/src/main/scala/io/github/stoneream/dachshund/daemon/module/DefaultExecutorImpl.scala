package io.github.stoneream.dachshund.daemon.module

import io.github.stoneream.dachshund.daemon.config.DaemonExecutorConfig
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor

import java.util.concurrent.Executors

class DefaultExecutorImpl(config: DaemonExecutorConfig)
    extends ExecutorServiceExecutionContext(
      name = "default-executor",
      executorService = Executors.newFixedThreadPool(
        config.threadCount,
        new NamedDaemonThreadFactory("default-executor")
      ),
      shutdownGracePeriod = config.shutdownGracePeriod
    )
    with DefaultExecutor
