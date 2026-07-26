package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class UserNewReleaseEventsSyncJobConfig(
    override val setting: JobSetting,
    batchSize: Int
) extends JobConfig

object UserNewReleaseEventsSyncJobConfig {
  private val Name: String = "user-new-release-events-sync"
  private val ConfigPath: String = "daemon.jobs.user-new-release-events-sync"

  private final case class RawUserNewReleaseEventsSyncJobConfig(
      enabled: Boolean,
      interval: FiniteDuration,
      timeout: FiniteDuration,
      retry: JobRetryPolicy,
      batchSize: Int
  ) derives ConfigReader {
    def settingConfig: JobSettingConfig =
      JobSettingConfig(
        enabled = enabled,
        interval = interval,
        timeout = timeout,
        retry = retry
      )
  }

  given ConfigReader[UserNewReleaseEventsSyncJobConfig] =
    summon[ConfigReader[RawUserNewReleaseEventsSyncJobConfig]].emap(validate)

  private def validate(raw: RawUserNewReleaseEventsSyncJobConfig): Either[CannotConvert, UserNewReleaseEventsSyncJobConfig] =
    for {
      setting <- raw.settingConfig.toJobSetting(Name, ConfigPath)
      batchSize <- DaemonConfigValidation.positiveInt(s"$ConfigPath.batch-size", raw.batchSize)
    } yield UserNewReleaseEventsSyncJobConfig(
      setting = setting,
      batchSize = batchSize
    )
}
