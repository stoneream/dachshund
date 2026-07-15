package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context

private[artist_releases_sync] object ArtistReleasesSyncFailureType {
  val InvalidClientCredentials = "invalid_client"
  val InsufficientScope = "insufficient_scope"
  val RateLimited = "rate_limited"
  val Network = "network"
  val ServerError = "server_error"
  val InvalidResponse = "invalid_response"
  val ClientError = "client_error"
  val Unknown = "unknown"
}
