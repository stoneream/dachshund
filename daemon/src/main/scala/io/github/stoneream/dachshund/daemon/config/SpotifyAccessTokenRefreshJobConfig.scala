package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class SpotifyAccessTokenRefreshJobConfig(
    override val setting: JobSetting,
    batchSize: Int
) extends JobConfig

object SpotifyAccessTokenRefreshJobConfig {
  private val Name: String = "spotify-access-token-refresh"
  private val ConfigPath: String = "daemon.jobs.spotify-access-token-refresh"

  private final case class RawSpotifyAccessTokenRefreshJobConfig(
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

  given ConfigReader[SpotifyAccessTokenRefreshJobConfig] =
    summon[ConfigReader[RawSpotifyAccessTokenRefreshJobConfig]].emap(validate)

  private def validate(raw: RawSpotifyAccessTokenRefreshJobConfig): Either[CannotConvert, SpotifyAccessTokenRefreshJobConfig] =
    for {
      setting <- raw.settingConfig.toJobSetting(Name, ConfigPath)
      batchSize <- DaemonConfigValidation.positiveInt(s"$ConfigPath.batch-size", raw.batchSize)
    } yield SpotifyAccessTokenRefreshJobConfig(
      setting = setting,
      batchSize = batchSize
    )
}
