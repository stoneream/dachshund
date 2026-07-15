package io.github.stoneream.dachshund.config.database

import pureconfig.ConfigReader

final case class HikariConfig(
    poolName: String,
    maximumPoolSize: Int,
    minimumIdle: Int,
    connectionTimeout: Long,
    idleTimeout: Long,
    maxLifetime: Long,
    validationTimeout: Long
) derives ConfigReader
