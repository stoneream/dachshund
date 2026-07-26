package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.user_new_release_notification_delivery.ReleaseTrackReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor

import scala.concurrent.Future

@Singleton
private[user_new_release_notification_delivery] class FindReleaseTrackUrisStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    releaseTrackReader: ReleaseTrackReader,
    databaseExecutor: DatabaseExecutor
) {
  def run(artistReleaseId: Long): Future[Seq[String]] =
    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        releaseTrackReader.findSpotifyTrackUrisByArtistReleaseId(artistReleaseId)
      }
    }(using databaseExecutor)
}
