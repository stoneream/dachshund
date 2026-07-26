package io.github.stoneream.dachshund.usecase.user_settings.apply

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as AccessTokenProviderException
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.user_settings.apply.{UserSettingsApplyUseCaseException as UseCaseException, UserSettingsApplyUseCaseInput as UseCaseInput, UserSettingsApplyUseCaseOutput as UseCaseOutput}
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyCreatePlaylistResult
import io.github.stoneream.dachshund.usecase.user_settings.apply.step.{CleanupSpotifyManagedPlaylistStep, CreateSpotifyManagedPlaylistStep, FindManagedPlaylistSettingStep, ResolveManagedPlaylistNameStep, ResolveSpotifyAccessTokenStep, UpdateManagedPlaylistSettingEnabledStep, WriteManagedPlaylistSettingStep}

import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class UserSettingsApplyUseCase @Inject() (
    findManagedPlaylistSettingStep: FindManagedPlaylistSettingStep,
    updateManagedPlaylistSettingEnabledStep: UpdateManagedPlaylistSettingEnabledStep,
    resolveSpotifyAccessTokenStep: ResolveSpotifyAccessTokenStep,
    resolveManagedPlaylistNameStep: ResolveManagedPlaylistNameStep,
    createSpotifyManagedPlaylistStep: CreateSpotifyManagedPlaylistStep,
    cleanupSpotifyManagedPlaylistStep: CleanupSpotifyManagedPlaylistStep,
    writeManagedPlaylistSettingStep: WriteManagedPlaylistSettingStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {
  private given DefaultExecutor = defaultExecutor

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] =
    input.userSessionContext match {
      case UserSessionContext.NotLoggedIn =>
        Future.failed(UseCaseException.NotLoggedIn)
      case user: UserSessionContext.NormalUser =>
        applySettings(user, input.newReleasePlaylistEnabled, input.now)
    }

  private def applySettings(
      user: UserSessionContext.NormalUser,
      newReleasePlaylistEnabled: Boolean,
      now: BusinessDateTime
  )(using LoggingContext): Future[UseCaseOutput] =
    (if (newReleasePlaylistEnabled) {
       enableNewReleasePlaylist(user, now)
     } else {
       disableNewReleasePlaylist(user.userId, now)
     })
      .map(_ => UseCaseOutput())(using defaultExecutor)
      .recoverWith(recoverApplyFailure)

  private def enableNewReleasePlaylist(
      user: UserSessionContext.NormalUser,
      now: BusinessDateTime
  )(using LoggingContext): Future[Unit] =
    findManagedPlaylistSettingStep
      .run(user.userId)
      .flatMap {
        case Some(setting) if setting.enabled == 1L =>
          Future.unit
        case Some(setting) =>
          updateManagedPlaylistSettingEnabledStep.run(setting.id, enabled = 1L, user.userId, now).map(_ => ())
        case None =>
          createManagedPlaylistSetting(user.userId, now)
      }(using defaultExecutor)

  private def disableNewReleasePlaylist(
      userId: Long,
      now: BusinessDateTime
  ): Future[Unit] =
    findManagedPlaylistSettingStep
      .run(userId)
      .flatMap {
        case Some(setting) if setting.enabled == 1L =>
          updateManagedPlaylistSettingEnabledStep.run(setting.id, enabled = 0L, userId, now).map(_ => ())
        case _ =>
          Future.unit
      }(using defaultExecutor)

  private def createManagedPlaylistSetting(
      userId: Long,
      now: BusinessDateTime
  )(using LoggingContext): Future[Unit] =
    for {
      accessToken <- resolveSpotifyAccessTokenStep.run(userId, now)
      playlistName <- resolveManagedPlaylistNameStep.run(accessToken)
      createdPlaylist <- createSpotifyManagedPlaylistStep.run(accessToken, playlistName)
      writeCount <- writeManagedPlaylistSettingStep.run(userId, createdPlaylist, now)
      _ <- cleanupUnusedPlaylistSetting(accessToken, createdPlaylist, writeCount)
    } yield ()

  private def cleanupUnusedPlaylistSetting(
      accessToken: String,
      createdPlaylist: SpotifyCreatePlaylistResult,
      writeCount: Int
  )(using LoggingContext): Future[Unit] =
    if (writeCount > 0) {
      Future.unit
    } else {
      cleanupSpotifyManagedPlaylistStep
        .run(accessToken, createdPlaylist.spotifyPlaylistCode)
        .recoverWith { case NonFatal(exception) =>
          Future.failed(UseCaseException.PlaylistSetupFailed(exception))
        }(using defaultExecutor)
    }

  private def recoverApplyFailure(using LoggingContext): PartialFunction[Throwable, Future[UseCaseOutput]] = {
    case exception: UseCaseException =>
      Future.failed(exception)
    case exception: AccessTokenProviderException.AuthorizationNotFound =>
      Future.failed(UseCaseException.SpotifyAuthorizationRequired(exception))
    case exception: AccessTokenProviderException.ReauthorizationRequired =>
      Future.failed(UseCaseException.SpotifyAuthorizationRequired(exception))
    case exception: AccessTokenProviderException.TemporaryFailure =>
      Future.failed(UseCaseException.SpotifyAuthorizationTemporarilyUnavailable(exception))
    case exception: AccessTokenProviderException =>
      Future.failed(UseCaseException.PlaylistSetupFailed(exception))
    case exception: SpotifyClientException.Forbidden =>
      Future.failed(UseCaseException.SpotifyAuthorizationRequired(exception))
    case exception: SpotifyClientException =>
      Future.failed(UseCaseException.PlaylistSetupFailed(exception))
    case NonFatal(exception) =>
      warn(
        "ユーザー設定適用中に想定外の失敗が発生しました",
        kv("failureClass", exception.getClass.getName)
      )
      Future.failed(UseCaseException.PlaylistSetupFailed(exception))
  }
}
