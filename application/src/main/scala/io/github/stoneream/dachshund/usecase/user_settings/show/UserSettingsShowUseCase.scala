package io.github.stoneream.dachshund.usecase.user_settings.show

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.generated.UserPlaylistSettingDbRow
import io.github.stoneream.dachshund.infra.db.reader.user_playlist_setting.UserPlaylistSettingReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.user_settings.UserSettingsManagedPlaylist
import io.github.stoneream.dachshund.usecase.user_settings.show.{UserSettingsShowUseCaseException as UseCaseException, UserSettingsShowUseCaseInput as UseCaseInput, UserSettingsShowUseCaseOutput as UseCaseOutput}

import scala.concurrent.Future

@Singleton
class UserSettingsShowUseCase @Inject() (
    databaseTransaction: DatabaseTransaction,
    userPlaylistSettingReader: UserPlaylistSettingReader,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ] {
  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] =
    findPlaylistSetting(input.user.userId).map { setting =>
      UseCaseOutput(
        user = UseCaseOutput.ViewUser(input.user.displayName),
        newReleasePlaylistEnabled = setting.exists(_.enabled == 1L),
        playlistName = setting.flatMap(playlistName).getOrElse(UserSettingsManagedPlaylist.BaseName),
        successMessage = input.successMessage,
        errorMessage = input.errorMessage
      )
    }(using defaultExecutor)

  private def findPlaylistSetting(userId: Long): Future[Option[UserPlaylistSettingDbRow]] =
    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        userPlaylistSettingReader.find(
          userId = userId,
          playlistUsageType = UserSettingsManagedPlaylist.UsageType
        )
      }
    }(using databaseExecutor)

  private def playlistName(row: UserPlaylistSettingDbRow): Option[String] =
    Option(row.playlistName).map(_.trim).filter(_.nonEmpty)
}
