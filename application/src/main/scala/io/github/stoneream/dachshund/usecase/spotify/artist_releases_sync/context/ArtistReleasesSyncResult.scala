package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context

private[artist_releases_sync] enum ArtistReleasesSyncResult {
  case PageProcessed
  case Blocked
  case TemporaryFailure
  case StaleLockSkipped
}
