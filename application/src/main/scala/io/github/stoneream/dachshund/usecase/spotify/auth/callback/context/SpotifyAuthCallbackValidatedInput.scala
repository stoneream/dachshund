package io.github.stoneream.dachshund.usecase.spotify.auth.callback.context

import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseInput.{SpotifyAuthorizationCode, SpotifyAuthorizationError, SpotifyAuthorizationState}

private[callback] final case class SpotifyAuthCallbackValidatedInput(
    code: Option[SpotifyAuthorizationCode],
    state: SpotifyAuthorizationState,
    externalAuthState: SpotifyAuthorizationState,
    error: Option[SpotifyAuthorizationError]
)
