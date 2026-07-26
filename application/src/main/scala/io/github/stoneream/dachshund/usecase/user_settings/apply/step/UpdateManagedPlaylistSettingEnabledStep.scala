package io.github.stoneream.dachshund.usecase.user_settings.apply.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.UserPlaylistSettingWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor

import scala.concurrent.Future

@Singleton
private[apply] class UpdateManagedPlaylistSettingEnabledStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userPlaylistSettingWriter: UserPlaylistSettingWriter,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      id: Long,
      enabled: Long,
      userId: Long,
      now: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        userPlaylistSettingWriter.updateEnabled(
          id = id,
          enabled = enabled,
          updatedAt = now,
          updatedUser = AuditUser.User(userId).dbValue
        )
      }
    }(using databaseExecutor)
}
