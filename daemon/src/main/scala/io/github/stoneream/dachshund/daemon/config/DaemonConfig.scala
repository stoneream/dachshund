package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader

final case class DaemonConfig(
    executors: DaemonExecutorsConfig,
    jobs: DaemonJobsConfig
) derives ConfigReader
