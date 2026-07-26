package io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_notification_delivery

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.config.{JobSetting, UserNewReleaseNotificationDeliveryJobConfig}
import io.github.stoneream.dachshund.daemon.job.model.Job
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import zio.Task

@Singleton
class UserNewReleaseNotificationDeliveryJob @Inject() (
    handler: UserNewReleaseNotificationDeliveryHandler,
    config: UserNewReleaseNotificationDeliveryJobConfig
) extends Job {
  override val setting: JobSetting = config.setting

  override def dispatch()(using LoggingContext): Task[Unit] =
    handler.handle()
}
