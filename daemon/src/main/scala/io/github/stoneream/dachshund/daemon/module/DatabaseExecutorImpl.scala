package io.github.stoneream.dachshund.daemon.module

import io.github.stoneream.dachshund.daemon.config.DaemonExecutorConfig
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor

import java.util.concurrent.Executors

class DatabaseExecutorImpl(config: DaemonExecutorConfig)
    extends ExecutorServiceExecutionContext(
      name = "database-executor",
      executorService = Executors.newFixedThreadPool(
        config.threadCount,
        new NamedDaemonThreadFactory("database-executor")
      ),
      shutdownGracePeriod = config.shutdownGracePeriod
    )
    with DatabaseExecutor
