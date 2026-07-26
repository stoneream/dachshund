package io.github.stoneream.dachshund.daemon.config

import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class JobSettingConfig(
    enabled: Boolean,
    interval: FiniteDuration,
    timeout: FiniteDuration,
    retry: JobRetryPolicy
) {
  def toJobSetting(name: String, path: String): Either[CannotConvert, JobSetting] =
    for {
      validatedName <- JobName.validate(name, s"$path.name")
      validatedSchedule <- JobSchedule.every(interval, s"$path.interval")
      validatedTimeout <- DaemonConfigValidation.positiveDuration(s"$path.timeout", timeout)
    } yield JobSetting(
      name = validatedName,
      enabled = enabled,
      schedule = validatedSchedule,
      timeout = validatedTimeout,
      retryPolicy = retry
    )
}
