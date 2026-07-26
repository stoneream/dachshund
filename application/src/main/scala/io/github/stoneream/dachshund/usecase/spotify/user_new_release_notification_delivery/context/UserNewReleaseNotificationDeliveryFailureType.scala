package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.context

private[user_new_release_notification_delivery] object UserNewReleaseNotificationDeliveryFailureType {
  val AuthorizationNotFound = "authorization_not_found"
  val ConcurrentUpdate = "access_token_concurrent_update"
  val InsufficientScope = "insufficient_scope"
  val RateLimited = "rate_limited"
  val Network = "network"
  val ServerError = "server_error"
  val InvalidResponse = "invalid_response"
  val ClientError = "client_error"
  val PlaylistClientError = "playlist_client_error"
  val ReleaseTracksNotFound = "release_tracks_not_found"
  val Unknown = "unknown"
}
