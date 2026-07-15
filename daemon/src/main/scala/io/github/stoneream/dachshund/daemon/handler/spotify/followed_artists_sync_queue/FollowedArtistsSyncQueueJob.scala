package io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync_queue

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.FollowedArtistsSyncQueueJobConfig
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class FollowedArtistsSyncQueueJob @Inject() (
    handler: FollowedArtistsSyncQueueHandler,
    config: FollowedArtistsSyncQueueJobConfig
) extends Job {
  override val setting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
