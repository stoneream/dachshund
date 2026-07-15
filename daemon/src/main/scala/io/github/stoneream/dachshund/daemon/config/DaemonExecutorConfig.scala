package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class DaemonExecutorConfig(
    threadCount: Int,
    shutdownGracePeriod: FiniteDuration
)

object DaemonExecutorConfig {
  private final case class RawDaemonExecutorConfig(
      threadCount: Int,
      shutdownGracePeriod: FiniteDuration
  ) derives ConfigReader

  given ConfigReader[DaemonExecutorConfig] =
    summon[ConfigReader[RawDaemonExecutorConfig]].emap(validate)

  private def validate(raw: RawDaemonExecutorConfig): Either[CannotConvert, DaemonExecutorConfig] =
    for {
      threadCount <- DaemonConfigValidation.positiveInt("daemon.executors.*.thread-count", raw.threadCount)
      shutdownGracePeriod <- DaemonConfigValidation.nonNegativeDuration(
        "daemon.executors.*.shutdown-grace-period",
        raw.shutdownGracePeriod
      )
    } yield DaemonExecutorConfig(
      threadCount = threadCount,
      shutdownGracePeriod = shutdownGracePeriod
    )
}
