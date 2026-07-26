package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class UserNewReleaseNotificationDeliveryJobConfig(
    override val setting: JobSetting,
    batchSize: Int,
    processingLease: FiniteDuration
) extends JobConfig

object UserNewReleaseNotificationDeliveryJobConfig {
  private val Name: String = "user-new-release-notification-delivery"
  private val ConfigPath: String = "daemon.jobs.user-new-release-notification-delivery"

  private final case class RawUserNewReleaseNotificationDeliveryJobConfig(
      enabled: Boolean,
      interval: FiniteDuration,
      timeout: FiniteDuration,
      retry: JobRetryPolicy,
      batchSize: Int,
      processingLease: FiniteDuration
  ) derives ConfigReader {
    def settingConfig: JobSettingConfig =
      JobSettingConfig(
        enabled = enabled,
        interval = interval,
        timeout = timeout,
        retry = retry
      )
  }

  given ConfigReader[UserNewReleaseNotificationDeliveryJobConfig] =
    summon[ConfigReader[RawUserNewReleaseNotificationDeliveryJobConfig]].emap(validate)

  private def validate(raw: RawUserNewReleaseNotificationDeliveryJobConfig): Either[CannotConvert, UserNewReleaseNotificationDeliveryJobConfig] =
    for {
      setting <- raw.settingConfig.toJobSetting(Name, ConfigPath)
      batchSize <- DaemonConfigValidation.positiveInt(s"$ConfigPath.batch-size", raw.batchSize)
      processingLease <- DaemonConfigValidation.positiveDuration(s"$ConfigPath.processing-lease", raw.processingLease)
    } yield UserNewReleaseNotificationDeliveryJobConfig(
      setting = setting,
      batchSize = batchSize,
      processingLease = processingLease
    )
}
