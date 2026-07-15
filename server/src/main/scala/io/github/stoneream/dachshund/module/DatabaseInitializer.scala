package io.github.stoneream.dachshund.module

import com.google.inject.{Inject, Singleton}
import com.zaxxer.hikari.HikariDataSource
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.config.database.{DatabaseConfig, DatabasePoolConfig}
import io.github.stoneream.dachshund.logging.Logger
import play.api.inject.ApplicationLifecycle
import scalikejdbc.{ConnectionPool, DataSourceCloser, DataSourceConnectionPool}

import scala.concurrent.Future

@Singleton
class DatabaseInitializer @Inject() (
    applicationConfig: ApplicationConfig,
    lifecycle: ApplicationLifecycle
) extends Logger {
  private val databaseConfig: DatabaseConfig = applicationConfig.db

  private val registeredPools = databaseConfig.allPools.map(registerPool)
  logger.info(
    "コネクションプールを初期化しました",
    kv("db.poolCount", registeredPools.size),
    kv("db.poolNames", registeredPools.map(_.name).mkString(","))
  )

  lifecycle.addStopHook { () =>
    Future.successful {
      registeredPools.foreach(ConnectionPool.close)
      logger.info(
        "コネクションプールをクローズしました",
        kv("db.poolCount", registeredPools.size),
        kv("db.poolNames", registeredPools.map(_.name).mkString(","))
      )
    }
  }

  private def registerPool(poolConfig: DatabasePoolConfig): Symbol = {
    val poolName = poolConfig.connectionPoolName
    val dataSource = buildDataSource(poolConfig)
    ConnectionPool.add(
      poolName,
      new DataSourceConnectionPool(
        dataSource,
        closer = () => dataSource.close()
      )
    )
    poolName
  }

  private def buildDataSource(poolConfig: DatabasePoolConfig): HikariDataSource = {
    val dataSource = new HikariDataSource()
    val hikariConfig = poolConfig.hikari

    dataSource.setJdbcUrl(poolConfig.requireUrl)
    dataSource.setUsername(poolConfig.requireUser)
    poolConfig.password.foreach(dataSource.setPassword)
    poolConfig.configuredDriver.foreach(dataSource.setDriverClassName)
    dataSource.setPoolName(hikariConfig.poolName)
    dataSource.setMaximumPoolSize(hikariConfig.maximumPoolSize)
    dataSource.setMinimumIdle(hikariConfig.minimumIdle)
    dataSource.setConnectionTimeout(hikariConfig.connectionTimeout)
    dataSource.setIdleTimeout(hikariConfig.idleTimeout)
    dataSource.setMaxLifetime(hikariConfig.maxLifetime)
    dataSource.setValidationTimeout(hikariConfig.validationTimeout)

    dataSource
  }
}
