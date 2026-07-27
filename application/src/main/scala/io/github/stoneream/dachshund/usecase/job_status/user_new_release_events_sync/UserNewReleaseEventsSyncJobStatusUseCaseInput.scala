package io.github.stoneream.dachshund.usecase.job_status.user_new_release_events_sync

import io.github.stoneream.dachshund.auth.UserSessionContext

final case class UserNewReleaseEventsSyncJobStatusUseCaseInput(
    user: UserSessionContext.NormalUser,
    detailPage: Int,
    detailLimit: Int
)
