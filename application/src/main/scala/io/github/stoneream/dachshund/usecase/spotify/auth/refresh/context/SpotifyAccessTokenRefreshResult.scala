package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context

private[refresh] enum SpotifyAccessTokenRefreshResult {
  case Refreshed
  case ReauthorizationRequired
  case TemporaryFailure
  case StaleLockSkipped
}
