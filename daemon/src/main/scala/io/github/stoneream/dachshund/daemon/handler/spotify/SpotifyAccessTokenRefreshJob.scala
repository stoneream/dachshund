package io.github.stoneream.dachshund.daemon.handler.spotify

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.{JobSetting, SpotifyAccessTokenRefreshJobConfig}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class SpotifyAccessTokenRefreshJob @Inject() (
    handler: SpotifyAccessTokenRefreshJobHandler,
    config: SpotifyAccessTokenRefreshJobConfig
) extends Job {
  override val setting: JobSetting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
