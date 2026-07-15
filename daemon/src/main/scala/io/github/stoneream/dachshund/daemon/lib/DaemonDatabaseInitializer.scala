package io.github.stoneream.dachshund.daemon.lib

import com.google.inject.{Inject, Singleton}
import com.zaxxer.hikari.HikariDataSource
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.config.database.{DatabaseConfig, DatabasePoolConfig}
import io.github.stoneream.dachshund.logging.Logger
import scalikejdbc.{ConnectionPool, DataSourceCloser, DataSourceConnectionPool}
import zio.{Scope, UIO, ZIO}

import scala.util.control.NonFatal

@Singleton
final class DaemonDatabaseInitializer @Inject() (applicationConfig: ApplicationConfig) extends Logger {
  private val databaseConfig: DatabaseConfig = applicationConfig.db

  def scoped: ZIO[Scope, Throwable, Unit] = {
    ZIO
      .acquireRelease(
        ZIO.attemptBlocking(start())
      )(stopSafely)
      .unit
  }

  private def start(): Vector[Symbol] = {
    val registeredPools = databaseConfig.allPools.foldLeft(Vector.empty[Symbol]) { (registeredPools, poolConfig) =>
      try {
        registeredPools :+ registerPool(poolConfig)
      } catch {
        case NonFatal(exception) =>
          closePoolsSuppressingFailure(registeredPools, exception)
          throw exception
      }
    }

    logger.info(
      "daemon コネクションプールを初期化しました",
      kv("db.poolCount", registeredPools.size),
      kv("db.poolNames", registeredPools.map(_.name).mkString(","))
    )

    registeredPools
  }

  private def stopSafely(registeredPools: Vector[Symbol]): UIO[Unit] =
    ZIO.attemptBlocking(stop(registeredPools)).catchAll { exception =>
      ZIO.succeed {
        logger.warn(
          "daemon コネクションプールのクローズに失敗しました",
          kv("db.closeFailureClass", exception.getClass.getName),
          kv("db.closeFailureMessage", Option(exception.getMessage).getOrElse("")),
          kv("db.suppressedFailureCount", exception.getSuppressed.length)
        )
      }
    }

  private def stop(registeredPools: Vector[Symbol]): Unit = {
    if (registeredPools.nonEmpty) {
      closePools(registeredPools)
      logger.info(
        "daemon コネクションプールをクローズしました",
        kv("db.poolCount", registeredPools.size),
        kv("db.poolNames", registeredPools.map(_.name).mkString(","))
      )
    } else {}
  }

  private def closePools(registeredPools: Vector[Symbol]): Unit =
    registeredPools
      .foldLeft(Option.empty[Throwable]) { (failure, poolName) =>
        try {
          ConnectionPool.close(poolName)
          failure
        } catch {
          case NonFatal(exception) =>
            failure match {
              case Some(existingFailure) =>
                existingFailure.addSuppressed(exception)
                Some(existingFailure)
              case None =>
                Some(exception)
            }
        }
      }
      .foreach(exception => throw exception)

  private def closePoolsSuppressingFailure(registeredPools: Vector[Symbol], exception: Throwable): Unit =
    try {
      closePools(registeredPools)
    } catch {
      case NonFatal(closeFailure) =>
        exception.addSuppressed(closeFailure)
    }

  private def registerPool(poolConfig: DatabasePoolConfig): Symbol = {
    val poolName = poolConfig.connectionPoolName
    val dataSource = buildDataSource(poolConfig)
    try {
      ConnectionPool.add(
        poolName,
        new DataSourceConnectionPool(
          dataSource,
          closer = dataSourceCloser(dataSource)
        )
      )
    } catch {
      case NonFatal(exception) =>
        closeDataSourceSuppressingFailure(dataSource, exception)
        throw exception
    }
    poolName
  }

  private def dataSourceCloser(dataSource: HikariDataSource): DataSourceCloser =
    new DataSourceCloser {
      override def close(): Unit =
        dataSource.close()
    }

  private def closeDataSourceSuppressingFailure(dataSource: HikariDataSource, exception: Throwable): Unit =
    try {
      dataSource.close()
    } catch {
      case NonFatal(closeFailure) =>
        exception.addSuppressed(closeFailure)
    }

  private def buildDataSource(poolConfig: DatabasePoolConfig): HikariDataSource = {
    val dataSource = new HikariDataSource()
    val hikariConfig = poolConfig.hikari

    try {
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
    } catch {
      case NonFatal(exception) =>
        closeDataSourceSuppressingFailure(dataSource, exception)
        throw exception
    }
  }
}
