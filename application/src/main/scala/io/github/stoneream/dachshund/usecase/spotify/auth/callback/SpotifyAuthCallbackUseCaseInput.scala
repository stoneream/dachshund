package io.github.stoneream.dachshund.usecase.spotify.auth.callback

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class SpotifyAuthCallbackUseCaseInput(
    code: Option[SpotifyAuthCallbackUseCaseInput.SpotifyAuthorizationCode],
    state: Option[SpotifyAuthCallbackUseCaseInput.SpotifyAuthorizationState],
    externalAuthState: Option[SpotifyAuthCallbackUseCaseInput.SpotifyAuthorizationState],
    error: Option[SpotifyAuthCallbackUseCaseInput.SpotifyAuthorizationError],
    now: BusinessDateTime
)

object SpotifyAuthCallbackUseCaseInput {
  final case class SpotifyAuthorizationCode(value: String)

  final case class SpotifyAuthorizationState(value: String)

  final case class SpotifyAuthorizationError(value: String)
}
