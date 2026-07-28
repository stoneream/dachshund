package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

private[artist_releases_sync] enum ArtistReleasesSyncResult {
  case PageProcessed
  case Blocked
  case TemporaryFailure(failureType: String, nextAttemptAt: BusinessDateTime)
  case StaleLockSkipped
}
