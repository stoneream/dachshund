package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader

final case class DaemonExecutorsConfig(
    defaultExecutor: DaemonExecutorConfig,
    databaseExecutor: DaemonExecutorConfig,
    ioDispatcher: DaemonExecutorConfig
) derives ConfigReader
