package io.github.stoneream.dachshund.daemon.handler.spotify.artist_release_sync_queue

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.ArtistReleaseSyncQueueJobConfig
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class ArtistReleaseSyncQueueJob @Inject() (
    handler: ArtistReleaseSyncQueueHandler,
    config: ArtistReleaseSyncQueueJobConfig
) extends Job {
  override val setting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
