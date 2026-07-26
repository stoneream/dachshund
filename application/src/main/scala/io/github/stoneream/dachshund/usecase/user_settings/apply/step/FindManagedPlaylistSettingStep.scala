package io.github.stoneream.dachshund.usecase.user_settings.apply.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.generated.UserPlaylistSettingDbRow
import io.github.stoneream.dachshund.infra.db.reader.user_playlist_setting.UserPlaylistSettingReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.usecase.user_settings.UserSettingsManagedPlaylist

import scala.concurrent.Future

@Singleton
private[apply] class FindManagedPlaylistSettingStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userPlaylistSettingReader: UserPlaylistSettingReader,
    databaseExecutor: DatabaseExecutor
) {
  def run(userId: Long): Future[Option[UserPlaylistSettingDbRow]] =
    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        userPlaylistSettingReader.find(
          userId = userId,
          playlistUsageType = UserSettingsManagedPlaylist.UsageType
        )
      }
    }(using databaseExecutor)
}
