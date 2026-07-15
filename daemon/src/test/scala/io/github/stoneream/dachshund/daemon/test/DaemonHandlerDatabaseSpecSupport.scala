package io.github.stoneream.dachshund.daemon.test

import com.google.inject.util.Modules
import com.google.inject.{AbstractModule, Guice, Injector, Module}
import io.github.stoneream.dachshund.daemon.config.DaemonConfig
import io.github.stoneream.dachshund.daemon.module.DaemonModule
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor, IoDispatcher}
import io.github.stoneream.dachshund.test.lib.db.DatabaseSupport
import org.mockito.Mockito.{mock, when}
import org.scalatest.Suite

import scala.jdk.CollectionConverters.*

trait DaemonHandlerDatabaseSpecSupport extends DatabaseSupport with DaemonHandlerTestRuntime { this: Suite =>
  protected def createInjector(
      now: BusinessDateTime,
      extraOverrideModules: Module*
  ): Injector = {
    val baseModule = new DaemonModule(
      applicationConfig = Some(testApplicationConfig),
      daemonConfig = Some(testDaemonConfig)
    )
    val dateTimeService = dateTimeServiceReturning(now)
    val testModule = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[DateTimeService]).toInstance(dateTimeService)
        bind(classOf[DefaultExecutor]).toInstance(DirectDaemonExecutor)
        bind(classOf[DatabaseExecutor]).toInstance(DirectDaemonExecutor)
        bind(classOf[IoDispatcher]).toInstance(DirectDaemonExecutor)
      }
    }

    Guice.createInjector(
      Modules
        .`override`(baseModule)
        .`with`((Seq(testModule) ++ extraOverrideModules).asJava)
    )
  }

  private def dateTimeServiceReturning(now: BusinessDateTime): DateTimeService = {
    val dateTimeService = mock(classOf[DateTimeService])
    when(dateTimeService.now()).thenReturn(now)
    dateTimeService
  }

  protected lazy val testDaemonConfig: DaemonConfig =
    DaemonHandlerTestDaemonConfig.default
}
