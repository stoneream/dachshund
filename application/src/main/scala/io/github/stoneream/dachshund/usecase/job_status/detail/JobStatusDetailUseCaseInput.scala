package io.github.stoneream.dachshund.usecase.job_status.detail

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus

final case class JobStatusDetailUseCaseInput(
    now: BusinessDateTime,
    user: UserSessionContext.NormalUser,
    selectedStatuses: Set[QueueJobStatus],
    detailPage: Int,
    detailLimit: Int
)
