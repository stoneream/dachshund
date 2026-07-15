package io.github.stoneream.dachshund.service.spotify.oauth_client.payload

import io.circe.Decoder
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient

object SpotifyTokenResponseDecoder {
  given Decoder[SpotifyOAuthClient.TokenResponse] =
    Decoder.forProduct5(
      "access_token",
      "token_type",
      "expires_in",
      "refresh_token",
      "scope"
    )(SpotifyOAuthClient.TokenResponse.apply)
}
