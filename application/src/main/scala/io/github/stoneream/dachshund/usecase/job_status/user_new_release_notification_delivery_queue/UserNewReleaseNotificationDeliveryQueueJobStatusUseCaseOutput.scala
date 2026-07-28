package io.github.stoneream.dachshund.usecase.job_status.user_new_release_notification_delivery_queue

import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailPageContext, JobStatusDetailUseCaseOutput}

final case class UserNewReleaseNotificationDeliveryQueueJobStatusUseCaseOutput(
    context: JobStatusDetailPageContext
) extends JobStatusDetailUseCaseOutput
