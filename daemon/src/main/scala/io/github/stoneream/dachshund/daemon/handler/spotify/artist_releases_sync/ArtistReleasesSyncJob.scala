package io.github.stoneream.dachshund.daemon.handler.spotify.artist_releases_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.{ArtistReleasesSyncJobConfig, JobSetting}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class ArtistReleasesSyncJob @Inject() (
    handler: ArtistReleasesSyncHandler,
    config: ArtistReleasesSyncJobConfig
) extends Job {
  override val setting: JobSetting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
