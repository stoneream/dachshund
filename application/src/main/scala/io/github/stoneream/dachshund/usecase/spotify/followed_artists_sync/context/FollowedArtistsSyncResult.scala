package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

private[followed_artists_sync] enum FollowedArtistsSyncResult {
  case PageProcessed
  case Blocked
  case TemporaryFailure(failureType: String, nextAttemptAt: BusinessDateTime)
  case StaleLockSkipped
}
