package io.github.stoneream.dachshund.usecase.spotify.auth.refresh

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseTransaction
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyAuthorizationRefreshQueueWriter, SpotifyAuthorizationWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.SpotifyTokenEncryptor
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.{SpotifyAccessTokenRefreshUseCaseException => UseCaseException, SpotifyAccessTokenRefreshUseCaseInput => UseCaseInput, SpotifyAccessTokenRefreshUseCaseOutput => UseCaseOutput}
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.SpotifyAccessTokenRefreshResult
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.SpotifyAccessTokenRefreshResult.{ReauthorizationRequired, Refreshed, StaleLockSkipped, TemporaryFailure}
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.SpotifyAuthorizationRefreshTarget
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.SpotifyRefreshPreparationResult.{Failed, Prepared}
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step.{DecryptSpotifyRefreshTokenStep, FindSpotifyAuthorizationRefreshTargetsStep, HandleSpotifyRefreshFailureStep, HandleSpotifyRefreshSuccessStep, PrepareSpotifyRefreshSuccessStep, ReleaseSpotifyAuthorizationRefreshTargetsStep, RequestSpotifyAccessTokenRefreshStep, SpotifyRefreshFailureClassifier}

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.auth.refresh.SpotifyAuthorizationRefreshReader
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

@Singleton
class SpotifyAccessTokenRefreshUseCase @Inject() (
    findRefreshTargetsStep: FindSpotifyAuthorizationRefreshTargetsStep,
    decryptSpotifyRefreshTokenStep: DecryptSpotifyRefreshTokenStep,
    requestSpotifyAccessTokenRefreshStep: RequestSpotifyAccessTokenRefreshStep,
    prepareSpotifyRefreshSuccessStep: PrepareSpotifyRefreshSuccessStep,
    handleSpotifyRefreshSuccessStep: HandleSpotifyRefreshSuccessStep,
    handleSpotifyRefreshFailureStep: HandleSpotifyRefreshFailureStep,
    releaseSpotifyAuthorizationRefreshTargetsStep: ReleaseSpotifyAuthorizationRefreshTargetsStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {
  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
    given DefaultExecutor = defaultExecutor

    findRefreshTargetsStep
      .run(input.now, input.batchSize)
      .flatMap { targets =>
        logRefreshTargetsSelected(input.batchSize, targets.size)
        refreshTargets(targets, input.now)
          .map(_ => UseCaseOutput())
          .recoverWith { case NonFatal(exception) =>
            failAfterReleasingTargets(targets, input.now, exception)
          }
      }
      .recoverWith { case NonFatal(exception) =>
        exception match {
          case useCaseException: UseCaseException =>
            Future.failed(useCaseException)
          case _ =>
            Future.failed(UseCaseException.Unknown(exception))
        }
      }
  }

  private def failAfterReleasingTargets(
      targets: Seq[SpotifyAuthorizationRefreshTarget],
      now: BusinessDateTime,
      exception: Throwable
  )(using LoggingContext, DefaultExecutor): Future[UseCaseOutput] =
    releaseSpotifyAuthorizationRefreshTargetsStep
      .run(targets, now)
      .recoverWith { case NonFatal(releaseException) =>
        warn(
          "Spotify access token refresh の abort 後 release に失敗しました",
          kv("failureClass", releaseException.getClass.getName),
          kv("originalFailureClass", exception.getClass.getName)
        )
        Future.successful(0)
      }
      .flatMap(_ => Future.failed[UseCaseOutput](exception))

  private def refreshTargets(
      targets: Seq[SpotifyAuthorizationRefreshTarget],
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[Unit] =
    targets.foldLeft(Future.successful(())) { (futureDone, target) =>
      for {
        _ <- futureDone
        result <- refreshTarget(target, now)
      } yield logRefreshProgress(target, result, targets.size)
    }

  private def refreshTarget(
      target: SpotifyAuthorizationRefreshTarget,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[SpotifyAccessTokenRefreshResult] =
    decryptSpotifyRefreshTokenStep.run(target) match {
      case Left(failure) =>
        handleSpotifyRefreshFailureStep.run(target, failure, now)
      case Right(refreshToken) =>
        refreshAccessToken(target, refreshToken, now)
    }

  private def refreshAccessToken(
      target: SpotifyAuthorizationRefreshTarget,
      refreshToken: String,
      now: BusinessDateTime
  )(using LoggingContext, DefaultExecutor): Future[SpotifyAccessTokenRefreshResult] =
    requestSpotifyAccessTokenRefreshStep.run(refreshToken).transformWith {
      case Success(tokenResponse) =>
        prepareSpotifyRefreshSuccessStep.run(target, tokenResponse, refreshToken, now) match {
          case Prepared(refreshedTokens) =>
            handleSpotifyRefreshSuccessStep.run(target, refreshedTokens, now)
          case Failed(failure) =>
            handleSpotifyRefreshFailureStep.run(target, failure, now)
        }
      case Failure(exception) =>
        if (SpotifyRefreshFailureClassifier.isInvalidClientCredentials(exception)) {
          Future.failed(UseCaseException.InvalidClientCredentials(exception))
        } else {
          handleSpotifyRefreshFailureStep.run(target, SpotifyRefreshFailureClassifier.fromThrowable(exception), now)
        }
    }

  private def logRefreshTargetsSelected(
      batchSize: Int,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "Spotify access token refresh の対象を取得しました",
      kv("spotifyAccessTokenRefresh.batchSize", batchSize),
      kv("spotifyAccessTokenRefresh.selectedCount", selectedCount)
    )

  private def logRefreshProgress(
      target: SpotifyAuthorizationRefreshTarget,
      result: SpotifyAccessTokenRefreshResult,
      selectedCount: Int
  )(using LoggingContext): Unit =
    info(
      "Spotify access token refresh の処理が進みました",
      kv("spotifyAuthorizationId", target.authorizationId),
      kv("spotifyAuthorizationRefreshQueueId", target.queueId),
      kv("userId", target.userId),
      kv("spotifyAccessTokenRefresh.result", resultName(result)),
      kv("spotifyAccessTokenRefresh.selectedCount", selectedCount)
    )

  private def resultName(result: SpotifyAccessTokenRefreshResult): String =
    result match {
      case Refreshed => "refreshed"
      case ReauthorizationRequired => "reauthorization_required"
      case TemporaryFailure => "temporary_failure"
      case StaleLockSkipped => "stale_lock_skipped"
    }

}

object SpotifyAccessTokenRefreshUseCase {
  def make(
      applicationConfig: ApplicationConfig,
      databaseTransaction: DatabaseTransaction,
      refreshReader: SpotifyAuthorizationRefreshReader,
      authorizationWriter: SpotifyAuthorizationWriter,
      refreshQueueWriter: SpotifyAuthorizationRefreshQueueWriter,
      spotifyTokenEncryptor: SpotifyTokenEncryptor,
      spotifyOAuthClient: SpotifyOAuthClient,
      defaultExecutor: DefaultExecutor,
      databaseExecutor: DatabaseExecutor
  ): SpotifyAccessTokenRefreshUseCase =
    new SpotifyAccessTokenRefreshUseCase(
      findRefreshTargetsStep = new FindSpotifyAuthorizationRefreshTargetsStep(
        databaseTransaction = databaseTransaction,
        refreshReader = refreshReader,
        databaseExecutor = databaseExecutor
      ),
      decryptSpotifyRefreshTokenStep = new DecryptSpotifyRefreshTokenStep(
        spotifyTokenEncryptor = spotifyTokenEncryptor
      ),
      requestSpotifyAccessTokenRefreshStep = new RequestSpotifyAccessTokenRefreshStep(
        applicationConfig = applicationConfig,
        spotifyOAuthClient = spotifyOAuthClient
      ),
      prepareSpotifyRefreshSuccessStep = new PrepareSpotifyRefreshSuccessStep(
        applicationConfig = applicationConfig,
        spotifyTokenEncryptor = spotifyTokenEncryptor
      ),
      handleSpotifyRefreshSuccessStep = new HandleSpotifyRefreshSuccessStep(
        databaseTransaction = databaseTransaction,
        authorizationWriter = authorizationWriter,
        refreshQueueWriter = refreshQueueWriter,
        databaseExecutor = databaseExecutor,
        defaultExecutor = defaultExecutor
      ),
      handleSpotifyRefreshFailureStep = new HandleSpotifyRefreshFailureStep(
        applicationConfig = applicationConfig,
        databaseTransaction = databaseTransaction,
        refreshQueueWriter = refreshQueueWriter,
        databaseExecutor = databaseExecutor,
        defaultExecutor = defaultExecutor
      ),
      releaseSpotifyAuthorizationRefreshTargetsStep = new ReleaseSpotifyAuthorizationRefreshTargetsStep(
        databaseTransaction = databaseTransaction,
        refreshQueueWriter = refreshQueueWriter,
        databaseExecutor = databaseExecutor
      ),
      defaultExecutor = defaultExecutor
    )
}
