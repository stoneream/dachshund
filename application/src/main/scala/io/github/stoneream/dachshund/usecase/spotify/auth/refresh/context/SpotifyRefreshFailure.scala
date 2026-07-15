package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context

import scala.concurrent.duration.FiniteDuration

private[refresh] final case class SpotifyRefreshFailure(
    failureType: String,
    retryAfter: Option[FiniteDuration] = None
)
