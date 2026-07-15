package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class FollowedArtistsSyncJobConfig(
    override val setting: JobSetting,
    batchSize: Int,
    processingLease: FiniteDuration
) extends JobConfig

object FollowedArtistsSyncJobConfig {
  private val Name: String = "followed-artists-sync"
  private val ConfigPath: String = "daemon.jobs.followed-artists-sync"

  private final case class RawFollowedArtistsSyncJobConfig(
      interval: FiniteDuration,
      timeout: FiniteDuration,
      retry: JobRetryPolicy,
      batchSize: Int,
      processingLease: FiniteDuration
  ) derives ConfigReader {
    def settingConfig: JobSettingConfig =
      JobSettingConfig(
        interval = interval,
        timeout = timeout,
        retry = retry
      )
  }

  given ConfigReader[FollowedArtistsSyncJobConfig] =
    summon[ConfigReader[RawFollowedArtistsSyncJobConfig]].emap(validate)

  private def validate(raw: RawFollowedArtistsSyncJobConfig): Either[CannotConvert, FollowedArtistsSyncJobConfig] =
    for {
      setting <- raw.settingConfig.toJobSetting(Name, ConfigPath)
      batchSize <- DaemonConfigValidation.positiveInt(s"$ConfigPath.batch-size", raw.batchSize)
      processingLease <- DaemonConfigValidation.positiveDuration(s"$ConfigPath.processing-lease", raw.processingLease)
    } yield FollowedArtistsSyncJobConfig(
      setting = setting,
      batchSize = batchSize,
      processingLease = processingLease
    )
}
