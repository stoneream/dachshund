package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class ArtistReleaseSyncQueueJobConfig(
    override val setting: JobSetting
) extends JobConfig

object ArtistReleaseSyncQueueJobConfig {
  private val Name: String = "artist-release-sync-queue"
  private val ConfigPath: String = "daemon.jobs.artist-release-sync-queue"

  private final case class RawArtistReleaseSyncQueueJobConfig(
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

  given ConfigReader[ArtistReleaseSyncQueueJobConfig] =
    summon[ConfigReader[RawArtistReleaseSyncQueueJobConfig]].emap { raw =>
      raw.settingConfig.toJobSetting(Name, ConfigPath).map(ArtistReleaseSyncQueueJobConfig(_))
    }
}
