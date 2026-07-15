package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseInput.SpotifyAuthorizationCode
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.SpotifyAuthCallbackValidatedInput

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotify認可レスポンスから認可コードを検証・取得
 */
@Singleton
private[callback] class ValidateSpotifyAuthorizationCodeStep @Inject() () {
  def run(input: SpotifyAuthCallbackValidatedInput): Future[SpotifyAuthorizationCode] =
    Future.successful(
      input.code.getOrElse(throw UseCaseException.InvalidCallback(Some(input.state)))
    )
}
