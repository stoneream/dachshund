package io.github.stoneream.dachshund.daemon.lib

import com.zaxxer.hikari.HikariDataSource
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.config.database.{DatabaseConfig, DatabasePoolConfig, HikariConfig}
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.ConnectionPool
import zio.{Exit, Runtime, Unsafe, ZIO, ZLayer}

class DaemonDatabaseInitializerSpec extends AnyFeatureSpec {
  Feature("daemon database initializer") {
    Scenario("scoped layer の終了時に registered pool と HikariDataSource をクローズする") {
      val poolName = s"daemon-db-initializer-${System.nanoTime()}"
      val poolSymbol = Symbol(poolName)
      val initializer = new DaemonDatabaseInitializer(applicationConfig(poolName))

      val dataSource = unsafeRun {
        ZIO.scoped {
          for {
            _ <- ZLayer.scoped(initializer.scoped).build
            _ <- ZIO.attempt(assert(ConnectionPool.isInitialized(poolSymbol)))
            dataSource <- ZIO.attempt(ConnectionPool.get(poolSymbol).dataSource.asInstanceOf[HikariDataSource])
            _ <- ZIO.attempt(assert(!dataSource.isClosed))
          } yield dataSource
        }
      }

      assert(!ConnectionPool.isInitialized(poolSymbol))
      assert(dataSource.isClosed)
    }
  }

  private def applicationConfig(poolName: String): ApplicationConfig =
    TestApplicationConfig().copy(
      db = DatabaseConfig(
        master = DatabasePoolConfig(
          name = poolName,
          driver = Some("com.mysql.cj.jdbc.Driver"),
          url = Some("jdbc:mysql://127.0.0.1:13306/dachshund_test?characterEncoding=utf-8&characterSetResults=utf-8&allowPublicKeyRetrieval=true&useSSL=false"),
          user = Some("dachshund_test"),
          password = Some("dachshund_test"),
          hikari = HikariConfig(
            poolName = poolName,
            maximumPoolSize = 1,
            minimumIdle = 0,
            connectionTimeout = 250,
            idleTimeout = 10000,
            maxLifetime = 30000,
            validationTimeout = 250
          )
        ),
        slave = None
      )
    )

  private def unsafeRun[A](task: ZIO[Any, Throwable, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task) match {
        case Exit.Success(value) => value
        case Exit.Failure(cause) => throw cause.squash
      }
    }
}
