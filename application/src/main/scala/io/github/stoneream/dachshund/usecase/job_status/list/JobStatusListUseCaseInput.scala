package io.github.stoneream.dachshund.usecase.job_status.list

import io.github.stoneream.dachshund.auth.UserSessionContext

final case class JobStatusListUseCaseInput(
    user: UserSessionContext.NormalUser
)
