package io.github.stoneream.dachshund.service.spotify.auth.access_token

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as ProviderException
import io.github.stoneream.dachshund.service.spotify.auth.access_token.context.{SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshFailureReason, SpotifyAccessTokenRefreshedTokens}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.model.SpotifyAccessTokenResolveTarget
import io.github.stoneream.dachshund.service.spotify.auth.access_token.step.{PrepareSpotifyAccessTokenRefreshSuccessStep, RequestSpotifyAccessTokenRefreshStep, SpotifyAccessTokenRefreshFailureClassifier, StoredSpotifyAccessTokenStep}

import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.control.NonFatal

@Singleton
class SpotifyAuthorizationCodeAccessTokenProviderImpl @Inject() (
    storedSpotifyAccessTokenStep: StoredSpotifyAccessTokenStep,
    requestSpotifyAccessTokenRefreshStep: RequestSpotifyAccessTokenRefreshStep,
    prepareSpotifyAccessTokenRefreshSuccessStep: PrepareSpotifyAccessTokenRefreshSuccessStep,
    defaultExecutor: DefaultExecutor
) extends SpotifyAuthorizationCodeAccessTokenProvider {

  override def resolve(input: SpotifyAuthorizationCodeAccessTokenResolveInput)(using LoggingContext): Future[ResolvedSpotifyAuthorizationCodeAccessToken] = {
    given DefaultExecutor = defaultExecutor

    (for {
      target <- storedSpotifyAccessTokenStep.findResolveTarget(input.userId)
      resolvedToken <- runResolveAction(target, input)
    } yield resolvedToken)
      .recoverWith {
        case exception: ProviderException =>
          Future.failed(exception)
        case NonFatal(exception) =>
          Future.failed(ProviderException.Unknown(exception))
      }
  }

  private def runResolveAction(
      target: SpotifyAccessTokenResolveTarget,
      input: SpotifyAuthorizationCodeAccessTokenResolveInput
  )(using LoggingContext, DefaultExecutor): Future[ResolvedSpotifyAuthorizationCodeAccessToken] = {
    decideResolveAction(target, input) match {
      case ResolveAction.UseCurrentAccessToken =>
        resolveCurrentAccessToken(target, input.now)
      case ResolveAction.RefreshAccessToken =>
        refreshAccessToken(target, input.now)
      case ResolveAction.FailConcurrentUpdate =>
        Future.failed(ProviderException.ConcurrentUpdate(input.userId))
      case ResolveAction.FailReauthorizationRequired(reasonType) =>
        Future.failed(
          ProviderException.ReauthorizationRequired(
            input.userId,
            reasonType
          )
        )
      case ResolveAction.FailTemporaryFailure(reasonType, nextAttemptAt) =>
        Future.failed(ProviderException.TemporaryFailure(input.userId, reasonType, nextAttemptAt))
    }
  }

  private def decideResolveAction(
      target: SpotifyAccessTokenResolveTarget,
      input: SpotifyAuthorizationCodeAccessTokenResolveInput
  ): ResolveAction = {
    val scheduledStatus = QueueJobStatus.Scheduled.dbValue
    val blockedStatus = QueueJobStatus.Blocked.dbValue

    target.queueStatus match {
      case `blockedStatus` =>
        ResolveAction.FailReauthorizationRequired(lastErrorTypeOrUnknown(target))
      case status if status != scheduledStatus =>
        if (canUseCurrentAccessToken(target, input)) {
          ResolveAction.UseCurrentAccessToken
        } else {
          ResolveAction.FailConcurrentUpdate
        }
      case _ if !shouldRefresh(target, input) =>
        ResolveAction.UseCurrentAccessToken
      case _ =>
        target.nextAttemptAt match {
          case Some(nextAttemptAt) if target.lastErrorType.nonEmpty && input.now.isBefore(nextAttemptAt) =>
            ResolveAction.FailTemporaryFailure(lastErrorTypeOrUnknown(target), nextAttemptAt)
          case _ =>
            ResolveAction.RefreshAccessToken
        }
    }
  }

  private def resolveCurrentAccessToken(
      target: SpotifyAccessTokenResolveTarget,
      now: BusinessDateTime
  )(using DefaultExecutor): Future[ResolvedSpotifyAuthorizationCodeAccessToken] = {
    for {
      accessToken <- decryptAccessToken(target, now)
    } yield ResolvedSpotifyAuthorizationCodeAccessToken(
      accessToken = accessToken,
      tokenType = target.tokenType,
      scopeText = target.scopeText,
      expiresAt = target.accessTokenExpiresAt
    )
  }

  private def decryptAccessToken(
      target: SpotifyAccessTokenResolveTarget,
      now: BusinessDateTime
  ): Future[String] =
    storedSpotifyAccessTokenStep.decryptAccessToken(target, now)

  private def refreshAccessToken(
      target: SpotifyAccessTokenResolveTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[ResolvedSpotifyAuthorizationCodeAccessToken] = {
    for {
      refreshToken <- decryptRefreshToken(target, now)
      refreshedTokens <- requestAndPrepareRefreshedTokens(target, refreshToken, now)
      resolvedToken <- storedSpotifyAccessTokenStep.persistRefreshSuccess(target, refreshedTokens, now)
    } yield resolvedToken
  }

  private def decryptRefreshToken(
      target: SpotifyAccessTokenResolveTarget,
      now: BusinessDateTime
  ): Future[String] =
    storedSpotifyAccessTokenStep.decryptRefreshToken(target, now)

  private def requestAndPrepareRefreshedTokens(
      target: SpotifyAccessTokenResolveTarget,
      refreshToken: String,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[SpotifyAccessTokenRefreshedTokens] = {
    for {
      tokenResponse <- requestSpotifyAccessTokenRefreshStep
        .run(refreshToken)
        .recoverWith { case NonFatal(exception) =>
          handleRefreshFailure(
            target,
            SpotifyAccessTokenRefreshFailureClassifier.fromThrowable(exception),
            now,
            Some(exception)
          )
        }
      refreshedTokens <- prepareSpotifyAccessTokenRefreshSuccessStep
        .run(target, tokenResponse, refreshToken, now)
        .fold(
          failure => handleRefreshFailure(target, failure, now),
          refreshedTokens => Future.successful(refreshedTokens)
        )
    } yield refreshedTokens
  }

  private def handleRefreshFailure(
      target: SpotifyAccessTokenResolveTarget,
      failure: SpotifyAccessTokenRefreshFailure,
      now: BusinessDateTime,
      cause: Option[Throwable] = None
  ): Future[Nothing] =
    storedSpotifyAccessTokenStep.persistRefreshFailure(target, failure, now, cause)

  private def shouldRefresh(
      target: SpotifyAccessTokenResolveTarget,
      input: SpotifyAuthorizationCodeAccessTokenResolveInput
  ): Boolean =
    input.forceRefresh || {
      val refreshBoundary = target.accessTokenExpiresAt.minus(target.refreshMarginSeconds.seconds)
      !input.now.isBefore(refreshBoundary)
    }

  private def canUseCurrentAccessToken(
      target: SpotifyAccessTokenResolveTarget,
      input: SpotifyAuthorizationCodeAccessTokenResolveInput
  ): Boolean =
    !input.forceRefresh && input.now.isBefore(target.accessTokenExpiresAt)

  private def lastErrorTypeOrUnknown(target: SpotifyAccessTokenResolveTarget): String =
    target.lastErrorType.getOrElse(SpotifyAccessTokenRefreshFailureReason.Unknown.dbValue)

  private enum ResolveAction {
    case UseCurrentAccessToken
    case RefreshAccessToken
    case FailConcurrentUpdate
    case FailReauthorizationRequired(reasonType: String)
    case FailTemporaryFailure(reasonType: String, nextAttemptAt: BusinessDateTime)
  }
}
