package io.github.stoneream.dachshund.usecase.job_status.followed_artists_sync

import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailPageContext, JobStatusDetailUseCaseOutput}

final case class FollowedArtistsSyncJobStatusUseCaseOutput(
    context: JobStatusDetailPageContext
) extends JobStatusDetailUseCaseOutput
