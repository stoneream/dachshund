package io.github.stoneream.dachshund.module

import io.github.stoneream.dachshund.config.{ApplicationConfig, ApplicationConfigReader}
import play.api.Configuration

import com.google.inject.{Inject, Provider, Singleton}

@Singleton
class ApplicationConfigLoader @Inject() (configuration: Configuration) extends Provider[ApplicationConfig] {
  private val loadedConfig = loadApplicationConfig()

  override def get(): ApplicationConfig = loadedConfig

  private def loadApplicationConfig(): ApplicationConfig =
    ApplicationConfigReader.load(configuration.underlying)
}
