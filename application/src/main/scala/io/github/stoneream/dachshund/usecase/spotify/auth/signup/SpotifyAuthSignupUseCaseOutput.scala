package io.github.stoneream.dachshund.usecase.spotify.auth.signup

final case class SpotifyAuthSignupUseCaseOutput(
    responseType: String,
    authorizationEndpoint: String,
    clientId: String,
    redirectUri: String,
    state: String,
    scope: String,
    stateMaxAgeSeconds: Long
)
