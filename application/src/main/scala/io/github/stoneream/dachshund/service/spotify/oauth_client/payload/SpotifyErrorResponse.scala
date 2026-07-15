package io.github.stoneream.dachshund.service.spotify.oauth_client.payload

final case class SpotifyErrorResponse(
    error: Option[String],
    errorDescription: Option[String]
)
