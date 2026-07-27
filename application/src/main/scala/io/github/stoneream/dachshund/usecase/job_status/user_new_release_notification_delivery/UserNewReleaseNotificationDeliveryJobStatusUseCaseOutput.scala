package io.github.stoneream.dachshund.usecase.job_status.user_new_release_notification_delivery

import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailPageContext, JobStatusDetailUseCaseOutput}

final case class UserNewReleaseNotificationDeliveryJobStatusUseCaseOutput(
    context: JobStatusDetailPageContext
) extends JobStatusDetailUseCaseOutput
