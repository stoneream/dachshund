package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_events_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.{JobSetting, UserNewReleaseEventsSyncJobConfig}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class UserNewReleaseEventsSyncJob @Inject() (
    handler: UserNewReleaseEventsSyncHandler,
    config: UserNewReleaseEventsSyncJobConfig
) extends Job {
  override val setting: JobSetting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
