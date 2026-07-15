package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context

import scala.concurrent.duration.FiniteDuration

private[artist_releases_sync] final case class ArtistReleasesSyncFailure(
    failureType: String,
    retryAfter: Option[FiniteDuration] = None
) extends Exception(s"artist releases sync failed: $failureType")
