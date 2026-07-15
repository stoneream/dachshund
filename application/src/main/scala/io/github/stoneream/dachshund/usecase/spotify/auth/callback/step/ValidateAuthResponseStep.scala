package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.{ExternalAuthFlowType, ExternalAuthProviderType, ExternalAuthRequestStatus}
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.SpotifyAuthCallbackValidatedInput
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.step.ResolveExternalAuthStep.ResolvedExternalAuth

import java.time.LocalDateTime
import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * 外部認証リクエストと認可レスポンスの正当性を検証
 */
@Singleton
private[callback] class ValidateAuthResponseStep @Inject() (
    defaultExecutor: DefaultExecutor
) extends TraceLogger {

  def run(
      resolvedExternalAuth: ResolvedExternalAuth,
      input: SpotifyAuthCallbackValidatedInput,
      now: BusinessDateTime
  )(using LoggingContext): Future[ResolvedExternalAuth] = {
    given DefaultExecutor = defaultExecutor
    val request = resolvedExternalAuth.externalAuthRequest

    for {
      _ <- ensureStateMatches(
        expectExternalAuthState = request.state,
        actualExternalAuthState = input.externalAuthState.value
      )
      _ <- ensureSignupFlow(request.flowType)
      _ <- ensureSpotifyProviderType(request.providerType)
      _ <- ensureAuthRequestPending(
        externalAuthRequestId = request.id,
        status = request.status
      )
      _ <- ensureAuthRequestNotExpired(
        externalAuthRequestId = request.id,
        externalAuthRequestExpiresAt = request.expiresAt,
        now = now
      )
    } yield resolvedExternalAuth
  }

  private def ensureStateMatches(
      expectExternalAuthState: String,
      actualExternalAuthState: String
  )(using LoggingContext): Future[Unit] =
    if (expectExternalAuthState != actualExternalAuthState) {
      info(
        "state が一致しないため Spotify コールバックを拒否しました",
        kv("expect", mask(expectExternalAuthState)),
        kv("actual", mask(actualExternalAuthState))
      )
      Future.failed(UseCaseException.InvalidState)
    } else {
      Future.unit
    }

  private def ensureSignupFlow(
      externalAuthFlowType: ExternalAuthFlowType
  )(using LoggingContext): Future[Unit] =
    if (externalAuthFlowType != ExternalAuthFlowType.Signup) {
      info(
        "フロー種別が想定外のため Spotify コールバックを拒否しました",
        kv("expect", ExternalAuthFlowType.Signup.dbValue),
        kv("actual", externalAuthFlowType.dbValue)
      )
      Future.failed(UseCaseException.InvalidState)
    } else {
      Future.unit
    }

  private def ensureSpotifyProviderType(
      providerType: ExternalAuthProviderType
  )(using LoggingContext): Future[Unit] =
    if (providerType != ExternalAuthProviderType.Spotify) {
      info(
        "プロバイダー種別が想定外のため Spotify コールバックを拒否しました",
        kv("expect", ExternalAuthProviderType.Spotify.dbValue),
        kv("actual", providerType.dbValue)
      )
      Future.failed(UseCaseException.InvalidState)
    } else {
      Future.unit
    }

  private def ensureAuthRequestPending(
      externalAuthRequestId: Long,
      status: ExternalAuthRequestStatus
  )(using LoggingContext): Future[Unit] =
    if (status != ExternalAuthRequestStatus.Pending) {
      info(
        "認可リクエストが使用済みのため Spotify コールバックを拒否しました",
        kv("externalAuthRequestId", externalAuthRequestId),
        kv("status", status.dbValue)
      )
      Future.failed(UseCaseException.AuthorizationRequestAlreadyUsed)
    } else {
      Future.unit
    }

  private def ensureAuthRequestNotExpired(
      externalAuthRequestId: Long,
      externalAuthRequestExpiresAt: LocalDateTime,
      now: BusinessDateTime
  )(using LoggingContext): Future[Unit] =
    if (externalAuthRequestExpiresAt.isBefore(now.toLocalDateTime)) {
      info(
        "認可リクエストが期限切れのため Spotify コールバックを拒否しました",
        kv("externalAuthRequestId", externalAuthRequestId)
      )
      Future.failed(UseCaseException.AuthorizationRequestExpired)
    } else {
      Future.unit
    }
}
