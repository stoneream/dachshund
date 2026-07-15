package io.github.stoneream.dachshund.service.spotify.auth.access_token.context

import scala.concurrent.duration.FiniteDuration

private[access_token] final case class SpotifyAccessTokenRefreshFailure(
    reason: SpotifyAccessTokenRefreshFailureReason,
    retryAfter: Option[FiniteDuration] = None
)
