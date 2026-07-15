package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context

private[followed_artists_sync] enum FollowedArtistsSyncResult {
  case PageProcessed
  case Blocked
  case TemporaryFailure
  case StaleLockSkipped
}
