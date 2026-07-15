package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context

import scala.concurrent.duration.FiniteDuration

private[followed_artists_sync] final case class FollowedArtistsSyncFailure(
    failureType: String,
    retryAfter: Option[FiniteDuration] = None
) extends Exception(s"followed artists sync failed: $failureType")
