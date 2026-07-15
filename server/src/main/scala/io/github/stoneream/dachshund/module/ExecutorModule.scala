package io.github.stoneream.dachshund.module

import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor, IoDispatcher}
import io.github.stoneream.dachshund.module.ExecutorModule.{DatabaseExecutorImpl, DefaultExecutorImpl, IoDispatcherImpl}
import org.apache.pekko.actor.ActorSystem
import play.api.inject.{Binding, Module}
import play.api.libs.concurrent.CustomExecutionContext
import play.api.{Configuration, Environment}

import com.google.inject.{Inject, Singleton}

class ExecutorModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = Seq(
    bind[DefaultExecutor].to[DefaultExecutorImpl].eagerly(),
    bind[DatabaseExecutor].to[DatabaseExecutorImpl].eagerly(),
    bind[IoDispatcher].to[IoDispatcherImpl].eagerly()
  )
}

object ExecutorModule {
  @Singleton
  class DefaultExecutorImpl @Inject() (actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "default-executor") with DefaultExecutor

  @Singleton
  class DatabaseExecutorImpl @Inject() (actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "database-executor") with DatabaseExecutor

  @Singleton
  class IoDispatcherImpl @Inject() (actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "io-dispatcher") with IoDispatcher
}
