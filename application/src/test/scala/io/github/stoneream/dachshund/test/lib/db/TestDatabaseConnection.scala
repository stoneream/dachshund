package io.github.stoneream.dachshund.test.lib.db

import io.github.stoneream.dachshund.config.ApplicationConfig
import scalikejdbc.ConnectionPool

private[lib] object TestDatabaseConnection {
  def initialize(applicationConfig: ApplicationConfig): Unit = {
    val poolConfig = applicationConfig.db.master
    val poolName = poolConfig.connectionPoolName

    if (ConnectionPool.isInitialized(poolName)) {
      ConnectionPool.close(poolName)
    }

    poolConfig.configuredDriver.foreach(Class.forName)
    ConnectionPool.add(
      poolName,
      poolConfig.requireUrl,
      poolConfig.requireUser,
      poolConfig.password.getOrElse("")
    )
  }

  def close(applicationConfig: ApplicationConfig): Unit = {
    val poolName = applicationConfig.db.master.connectionPoolName
    if (ConnectionPool.isInitialized(poolName)) {
      ConnectionPool.close(poolName)
    }
  }
}
