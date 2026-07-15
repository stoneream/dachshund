package io.github.stoneream.dachshund.daemon

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.config.database.{DatabaseConfig, DatabasePoolConfig, HikariConfig}
import io.github.stoneream.dachshund.daemon.job.JobScheduler
import io.github.stoneream.dachshund.daemon.lib.DaemonDatabaseInitializer
import io.github.stoneream.dachshund.daemon.module.DaemonLifecycle
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import io.github.stoneream.dachshund.usecase.daemon.ready.DaemonReadyUseCase
import org.scalatest.Suite
import zio.{Exit, Runtime, UIO, Unsafe, ZIO}

private[daemon] trait DaemonMainSpecSupport { this: Suite =>
  private val TestDatabaseUrl =
    "jdbc:mysql://127.0.0.1:13306/dachshund_test?characterEncoding=utf-8&characterSetResults=utf-8&allowPublicKeyRetrieval=true&useSSL=false"

  protected def daemonMain(
      applicationConfig: ApplicationConfig,
      spotifyOAuthClient: SpotifyOAuthClient,
      scheduler: JobScheduler
  ): DaemonMain =
    new DaemonMain(
      initializer = new DaemonDatabaseInitializer(applicationConfig),
      daemonReadyUseCase = new DaemonReadyUseCase(
        applicationConfig = applicationConfig,
        spotifyOAuthClient = spotifyOAuthClient,
        defaultExecutor = DirectDaemonMainExecutor
      ),
      scheduler = scheduler,
      lifecycle = new DaemonLifecycle(
        defaultExecutor = DirectDaemonMainExecutor,
        databaseExecutor = DirectDaemonMainExecutor,
        ioDispatcher = DirectDaemonMainExecutor
      )
    )

  protected def uniquePoolApplicationConfig(): ApplicationConfig = {
    val poolName = s"daemon-main-${System.nanoTime()}"

    TestApplicationConfig().copy(
      db = DatabaseConfig(
        master = DatabasePoolConfig(
          name = poolName,
          driver = Some("com.mysql.cj.jdbc.Driver"),
          url = Some(TestDatabaseUrl),
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
  }

  protected def runMainFailure(main: DaemonMain): Throwable =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(main.run)) match {
        case Exit.Success(_) => fail("daemon main unexpectedly completed")
        case Exit.Failure(cause) => cause.squash
      }
    }

  protected def unsafeRun[A](task: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task) match {
        case Exit.Success(value) => value
        case Exit.Failure(cause) => throw cause.squash
      }
    }

  protected object SchedulerReached extends RuntimeException("scheduler reached")

  protected final class ReachedScheduler extends JobScheduler {
    var runCount: Int = 0

    override def run(): ZIO[Any, Throwable, Nothing] =
      ZIO.succeed {
        runCount = runCount + 1
      } *> ZIO.fail(SchedulerReached)
  }
}
