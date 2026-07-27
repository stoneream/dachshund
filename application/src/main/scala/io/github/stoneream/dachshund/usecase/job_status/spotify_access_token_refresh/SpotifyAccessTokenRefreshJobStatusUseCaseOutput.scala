package io.github.stoneream.dachshund.usecase.job_status.spotify_access_token_refresh

import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailPageContext, JobStatusDetailUseCaseOutput}

final case class SpotifyAccessTokenRefreshJobStatusUseCaseOutput(
    context: JobStatusDetailPageContext
) extends JobStatusDetailUseCaseOutput
