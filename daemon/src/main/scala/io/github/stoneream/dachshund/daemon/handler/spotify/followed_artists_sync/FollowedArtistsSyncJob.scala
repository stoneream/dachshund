package io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.{FollowedArtistsSyncJobConfig, JobSetting}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class FollowedArtistsSyncJob @Inject() (
    handler: FollowedArtistsSyncHandler,
    config: FollowedArtistsSyncJobConfig
) extends Job {
  override val setting: JobSetting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
