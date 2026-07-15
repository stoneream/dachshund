package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context

private[refresh] object SpotifyRefreshFailureType {
  val InvalidGrant = "invalid_grant"
  val InsufficientScope = "insufficient_scope"
  val TokenDecryptFailed = "token_decrypt_failed"
  val RateLimited = "rate_limited"
  val Network = "network"
  val ServerError = "server_error"
  val InvalidResponse = "invalid_response"
  val ClientError = "client_error"
  val Unknown = "unknown"
}
