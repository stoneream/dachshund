package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_notification_delivery_queue

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.{JobSetting, UserNewReleaseNotificationDeliveryQueueJobConfig}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class UserNewReleaseNotificationDeliveryQueueJob @Inject() (
    handler: UserNewReleaseNotificationDeliveryQueueHandler,
    config: UserNewReleaseNotificationDeliveryQueueJobConfig
) extends Job {
  override val setting: JobSetting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
