package io.github.stoneream.dachshund.usecase.spotify.auth.signup

import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.auth.signup.step.{BuildSpotifyAuthorizationRequestStep, WriteSpotifyAuthorizationRequestStep}
import io.github.stoneream.dachshund.usecase.spotify.auth.signup.{SpotifyAuthSignupUseCaseException => UseCaseException, SpotifyAuthSignupUseCaseInput => UseCaseInput, SpotifyAuthSignupUseCaseOutput => UseCaseOutput}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SpotifyAuthSignupUseCase @Inject() (
    buildAuthorizationRequestStep: BuildSpotifyAuthorizationRequestStep,
    writeAuthorizationRequestStep: WriteSpotifyAuthorizationRequestStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ] {

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    for {
      authorizationRequest <- buildAuthorizationRequestStep.run()
      _ <- writeAuthorizationRequestStep.run(
        authorizationRequest = authorizationRequest,
        now = input.now
      )
    } yield UseCaseOutput(
      responseType = "code",
      authorizationEndpoint = authorizationRequest.authorizationEndpoint,
      clientId = authorizationRequest.clientId,
      redirectUri = authorizationRequest.redirectUri,
      state = authorizationRequest.state,
      scope = authorizationRequest.scopeText,
      stateMaxAgeSeconds = SpotifyAuthSignupUseCase.OAuthStateTtlSeconds
    )
  }
}

object SpotifyAuthSignupUseCase {
  private[signup] val OAuthStateTtlSeconds = 600L
}
