package io.github.stoneream.dachshund.usecase.job_status.artist_releases_sync

import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailPageContext, JobStatusDetailUseCaseOutput}

final case class ArtistReleasesSyncJobStatusUseCaseOutput(
    context: JobStatusDetailPageContext
) extends JobStatusDetailUseCaseOutput
