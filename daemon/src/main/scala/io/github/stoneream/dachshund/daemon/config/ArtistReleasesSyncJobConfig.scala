package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

import scala.concurrent.duration.FiniteDuration

final case class ArtistReleasesSyncJobConfig(
    override val setting: JobSetting,
    batchSize: Int,
    processingLease: FiniteDuration
) extends JobConfig

object ArtistReleasesSyncJobConfig {
  private val Name: String = "artist-releases-sync"
  private val ConfigPath: String = "daemon.jobs.artist-releases-sync"

  private final case class RawArtistReleasesSyncJobConfig(
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

  given ConfigReader[ArtistReleasesSyncJobConfig] =
    summon[ConfigReader[RawArtistReleasesSyncJobConfig]].emap(validate)

  private def validate(raw: RawArtistReleasesSyncJobConfig): Either[CannotConvert, ArtistReleasesSyncJobConfig] =
    for {
      setting <- raw.settingConfig.toJobSetting(Name, ConfigPath)
      batchSize <- DaemonConfigValidation.positiveInt(s"$ConfigPath.batch-size", raw.batchSize)
      processingLease <- DaemonConfigValidation.positiveDuration(s"$ConfigPath.processing-lease", raw.processingLease)
    } yield ArtistReleasesSyncJobConfig(
      setting = setting,
      batchSize = batchSize,
      processingLease = processingLease
    )
}
