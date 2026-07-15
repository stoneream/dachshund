package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.usecase.spotify.auth.callback.{SpotifyAuthCallbackUseCaseException as UseCaseException, SpotifyAuthCallbackUseCaseInput}
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.SpotifyAuthCallbackValidatedInput

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotify認可コールバックの必須パラメータを検証
 */
@Singleton
private[callback] class ValidateSpotifyAuthResponseStep @Inject() () {
  def run(input: SpotifyAuthCallbackUseCaseInput): Future[SpotifyAuthCallbackValidatedInput] = {
    val state = input.state.getOrElse(throw UseCaseException.InvalidCallback(None))
    val externalAuthState = input.externalAuthState.getOrElse(throw UseCaseException.InvalidCallback(Some(state)))

    Future.successful(
      SpotifyAuthCallbackValidatedInput(
        code = input.code,
        state = state,
        externalAuthState = externalAuthState,
        error = input.error
      )
    )
  }
}
