package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class FollowedArtistsSyncQueueJobConfig(
    override val setting: JobSetting
) extends JobConfig

object FollowedArtistsSyncQueueJobConfig {
  private val Name: String = "followed-artists-sync-queue"
  private val ConfigPath: String = "daemon.jobs.followed-artists-sync-queue"

  private final case class RawFollowedArtistsSyncQueueJobConfig(
      enabled: Boolean,
      interval: FiniteDuration,
      timeout: FiniteDuration,
      retry: JobRetryPolicy
  ) derives ConfigReader {
    def settingConfig: JobSettingConfig =
      JobSettingConfig(
        enabled = enabled,
        interval = interval,
        timeout = timeout,
        retry = retry
      )
  }

  given ConfigReader[FollowedArtistsSyncQueueJobConfig] =
    summon[ConfigReader[RawFollowedArtistsSyncQueueJobConfig]].emap { raw =>
      raw.settingConfig.toJobSetting(Name, ConfigPath).map(FollowedArtistsSyncQueueJobConfig(_))
    }
}
