package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context

private[followed_artists_sync] object FollowedArtistsSyncFailureType {
  val AuthorizationNotFound = "authorization_not_found"
  val ConcurrentUpdate = "access_token_concurrent_update"
  val InsufficientScope = "insufficient_scope"
  val RateLimited = "rate_limited"
  val Network = "network"
  val ServerError = "server_error"
  val InvalidResponse = "invalid_response"
  val ClientError = "client_error"
  val Unknown = "unknown"
}
