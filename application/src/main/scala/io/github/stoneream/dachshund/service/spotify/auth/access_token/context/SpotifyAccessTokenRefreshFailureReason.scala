package io.github.stoneream.dachshund.service.spotify.auth.access_token.context

private[access_token] enum SpotifyAccessTokenRefreshFailureReason(val dbValue: String) {
  case InvalidGrant extends SpotifyAccessTokenRefreshFailureReason("invalid_grant")
  case InsufficientScope extends SpotifyAccessTokenRefreshFailureReason("insufficient_scope")
  case TokenDecryptFailed extends SpotifyAccessTokenRefreshFailureReason("token_decrypt_failed")
  case RateLimited extends SpotifyAccessTokenRefreshFailureReason("rate_limited")
  case Network extends SpotifyAccessTokenRefreshFailureReason("network")
  case ServerError extends SpotifyAccessTokenRefreshFailureReason("server_error")
  case InvalidResponse extends SpotifyAccessTokenRefreshFailureReason("invalid_response")
  case ClientError extends SpotifyAccessTokenRefreshFailureReason("client_error")
  case Unknown extends SpotifyAccessTokenRefreshFailureReason("unknown")
}
