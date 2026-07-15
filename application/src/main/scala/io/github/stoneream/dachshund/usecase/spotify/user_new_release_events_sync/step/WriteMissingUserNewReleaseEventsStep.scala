package io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseEventDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseEventSource
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.UserNewReleaseEventsWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.context.MissingUserNewReleaseEvent

import scala.concurrent.Future

@Singleton
private[user_new_release_events_sync] class WriteMissingUserNewReleaseEventsStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userNewReleaseEventsWriter: UserNewReleaseEventsWriter,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      targets: Seq[MissingUserNewReleaseEvent],
      detectionSyncCode: String,
      detectedAt: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        targets.map { target =>
          userNewReleaseEventsWriter.write(
            UserNewReleaseEventSource(
              userId = target.userId,
              artistReleaseId = target.artistReleaseId,
              spotifyReleaseCode = target.spotifyReleaseCode,
              sourceSpotifyArtistCode = target.sourceSpotifyArtistCode,
              detectedAt = detectedAt,
              detectionSyncCode = detectionSyncCode,
              createdAt = detectedAt,
              updatedAt = detectedAt,
              deletedAt = Option.empty,
              createdUser = AuditUser.System,
              updatedUser = AuditUser.System,
              deletedUser = AuditUser.Empty,
              deleted = 0L,
              lockVersion = 0L
            ).toUserNewReleaseEventDbRow
          )
        }.sum
      }
    }(using databaseExecutor)
}
