package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class UserNewReleaseNotificationDeliveryQueueJobConfig(
    override val setting: JobSetting,
    batchSize: Int,
    processingLease: FiniteDuration
) extends JobConfig

object UserNewReleaseNotificationDeliveryQueueJobConfig {
  private val Name: String = "user-new-release-notification-delivery-queue"
  private val ConfigPath: String = "daemon.jobs.user-new-release-notification-delivery-queue"

  private final case class RawUserNewReleaseNotificationDeliveryQueueJobConfig(
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

  given ConfigReader[UserNewReleaseNotificationDeliveryQueueJobConfig] =
    summon[ConfigReader[RawUserNewReleaseNotificationDeliveryQueueJobConfig]].emap(validate)

  private def validate(raw: RawUserNewReleaseNotificationDeliveryQueueJobConfig): Either[CannotConvert, UserNewReleaseNotificationDeliveryQueueJobConfig] =
    for {
      setting <- raw.settingConfig.toJobSetting(Name, ConfigPath)
      batchSize <- DaemonConfigValidation.positiveInt(s"$ConfigPath.batch-size", raw.batchSize)
      processingLease <- DaemonConfigValidation.positiveDuration(s"$ConfigPath.processing-lease", raw.processingLease)
    } yield UserNewReleaseNotificationDeliveryQueueJobConfig(
      setting = setting,
      batchSize = batchSize,
      processingLease = processingLease
    )
}
