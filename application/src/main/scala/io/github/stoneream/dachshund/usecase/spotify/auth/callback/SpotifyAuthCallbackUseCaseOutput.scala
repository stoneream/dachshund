package io.github.stoneream.dachshund.usecase.spotify.auth.callback

import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseOutput.SpotifyAuthCallbackStatus

final case class SpotifyAuthCallbackUseCaseOutput(
    status: SpotifyAuthCallbackStatus,
    userId: Option[Long],
    sessionToken: Option[String]
)

object SpotifyAuthCallbackUseCaseOutput {
  enum SpotifyAuthCallbackStatus {
    case AuthorizationReceived
    case AuthorizationDenied
  }
}
