package io.github.stoneream.dachshund.service.spotify.oauth_client.payload

import io.circe.Decoder

object SpotifyErrorResponseDecoder {
  given Decoder[SpotifyErrorResponse] =
    Decoder.forProduct2("error", "error_description")(SpotifyErrorResponse.apply)
}
