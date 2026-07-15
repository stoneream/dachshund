package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context

private[refresh] enum SpotifyRefreshPreparationResult {
  case Prepared(refreshedTokens: SpotifyRefreshedTokens)
  case Failed(failure: SpotifyRefreshFailure)
}
