package io.github.stoneream.dachshund.usecase.spotify.auth.signup.step

private[signup] final case class SpotifyAuthorizationRequest(
    authorizationEndpoint: String,
    clientId: String,
    state: String,
    scopeText: String,
    redirectUri: String
)
