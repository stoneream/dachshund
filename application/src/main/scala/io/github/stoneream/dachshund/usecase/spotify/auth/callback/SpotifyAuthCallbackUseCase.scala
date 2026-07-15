package io.github.stoneream.dachshund.usecase.spotify.auth.callback

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.ExternalAuthRequest
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.SpotifyAuthCallbackValidatedInput
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseOutput.SpotifyAuthCallbackStatus
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.step.{CreateNewUserStep, EncryptSpotifyTokenStep, ExchangeSpotifyAuthorizationCodeStep, FinalizeExternalAuthRequestStep, HandleSpotifyProviderErrorStep, IssueUserSessionStep, NormalizeSpotifyScopeText, ResolveExternalAuthStep, ResolveSpotifyUserProfileStep, ResolveSpotifyUserStep, StartExternalAuthRequestStep, ValidateAuthResponseStep, ValidateSpotifyAuthResponseStep, ValidateSpotifyAuthorizationCodeStep, WriteSpotifyAuthorizationStep}
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.{SpotifyAuthCallbackUseCaseException => UseCaseException, SpotifyAuthCallbackUseCaseInput => UseCaseInput, SpotifyAuthCallbackUseCaseOutput => UseCaseOutput}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * Spotify認可コールバックを検証し、ユーザー解決または作成後に認可情報を保存
 */
@Singleton
class SpotifyAuthCallbackUseCase @Inject() (
    validateSpotifyAuthResponseStep: ValidateSpotifyAuthResponseStep,
    handleSpotifyProviderErrorStep: HandleSpotifyProviderErrorStep,
    validateSpotifyAuthorizationCodeStep: ValidateSpotifyAuthorizationCodeStep,
    resolveExternalAuthStep: ResolveExternalAuthStep,
    validateAuthResponseStep: ValidateAuthResponseStep,
    startExternalAuthRequestStep: StartExternalAuthRequestStep,
    finalizeExternalAuthRequestStep: FinalizeExternalAuthRequestStep,
    exchangeSpotifyAuthorizationCodeStep: ExchangeSpotifyAuthorizationCodeStep,
    resolveSpotifyUserProfileStep: ResolveSpotifyUserProfileStep,
    resolveSpotifyUserStep: ResolveSpotifyUserStep,
    createNewUserStep: CreateNewUserStep,
    encryptSpotifyTokenStep: EncryptSpotifyTokenStep,
    writeSpotifyAuthorizationStep: WriteSpotifyAuthorizationStep,
    issueUserSessionStep: IssueUserSessionStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    for {
      validatedInput <- validateSpotifyAuthResponseStep.run(input)
      resolvedExternalAuth <- resolveExternalAuthStep.run(validatedInput)
      validatedExternalAuth <- validateAuthResponseStep.run(resolvedExternalAuth, validatedInput, input.now)
      _ <- startExternalAuthRequestStep.run(validatedExternalAuth.externalAuthRequest, input.now)
      output <- runProcessingExternalAuthRequest(
        externalAuthRequest = validatedExternalAuth.externalAuthRequest,
        validatedInput = validatedInput,
        input = input
      )
    } yield output
  }

  private def runProcessingExternalAuthRequest(
      externalAuthRequest: ExternalAuthRequest,
      validatedInput: SpotifyAuthCallbackValidatedInput,
      input: UseCaseInput
  )(using LoggingContext, DefaultExecutor): Future[UseCaseOutput] =
    (for {
      _ <- handleSpotifyProviderErrorStep.run(validatedInput)
      code <- validateSpotifyAuthorizationCodeStep.run(validatedInput)
      tokenResponse <- exchangeSpotifyAuthorizationCodeStep.run(
        code = code,
        redirectUri = externalAuthRequest.redirectUri
      )
      spotifyProfile <- resolveSpotifyUserProfileStep.run(tokenResponse)
      resolvedUserOpt <- resolveSpotifyUserStep.run(spotifyProfile)
      userId <- resolvedUserOpt.fold {
        // ユーザーが解決できない = Spotify ID に紐づくユーザーが存在しない
        // そのため新規作成をする
        createNewUserStep.run(spotifyProfile, input.now).map(_.userId)
      } { resolvedUser =>
        Future.successful(resolvedUser.userId)
      }
      encryptedTokens <- encryptSpotifyTokenStep.run(
        userId = userId,
        tokenResponse = tokenResponse
      )
      _ <- writeSpotifyAuthorizationStep.run(
        userId = userId,
        externalAuthRequest = externalAuthRequest,
        tokenResponse = tokenResponse,
        encryptedTokens = encryptedTokens,
        now = input.now
      )
      issuedSessionToken <- issueUserSessionStep.run(userId, input.now)
      _ <- finalizeExternalAuthRequestStep.run(externalAuthRequest, input.now, AuditUser.User(userId))
      _ <- Future {
        info(
          "Spotify 認可コールバックが完了しました",
          kv("userId", userId),
          kv("spotifyUserId", mask(spotifyProfile.id)),
          kv("scope", NormalizeSpotifyScopeText(tokenResponse.scope.getOrElse(externalAuthRequest.scopes)))
        )
      }
    } yield UseCaseOutput(
      status = SpotifyAuthCallbackStatus.AuthorizationReceived,
      userId = Some(userId),
      sessionToken = Some(issuedSessionToken.value)
    )).recoverWith { case exception: UseCaseException =>
      // 認可リクエストを PROCESSING に更新済みのため、失敗時は FAILED に確定してから元の UseCaseException を再送出する
      finalizeExternalAuthRequestStep
        .run(externalAuthRequest, exception, input.now)
        .flatMap(_ => Future.failed(exception))
    }
}
