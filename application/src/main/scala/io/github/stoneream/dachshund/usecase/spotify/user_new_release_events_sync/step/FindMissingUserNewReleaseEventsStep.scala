package io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.user_new_release_events_sync.UserNewReleaseEventsReader
import io.github.stoneream.dachshund.infra.db.reader.user_new_release_events_sync.UserNewReleaseEventsReader.MissingEventRow
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.context.MissingUserNewReleaseEvent

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

@Singleton
private[user_new_release_events_sync] class FindMissingUserNewReleaseEventsStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userNewReleaseEventsReader: UserNewReleaseEventsReader,
    databaseExecutor: DatabaseExecutor
) {
  private val ReleaseLookback = 30.days

  def run(
      now: BusinessDateTime,
      batchSize: Int
  ): Future[Seq[MissingUserNewReleaseEvent]] = {
    val releasedFrom = now.minus(ReleaseLookback).toLocalDate.atStartOfDay()
    val releasedTo = BusinessDateTime.MAX.toLocalDateTime

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        userNewReleaseEventsReader
          .read(
            releasedFrom = releasedFrom,
            releasedTo = releasedTo,
            batchSize = batchSize
          )
          .map(toMissingUserNewReleaseEvent)
      }
    }(using databaseExecutor)
  }

  private def toMissingUserNewReleaseEvent(row: MissingEventRow): MissingUserNewReleaseEvent =
    MissingUserNewReleaseEvent(
      userId = row.userId,
      artistReleaseId = row.artistReleaseId,
      spotifyReleaseCode = row.spotifyReleaseCode,
      sourceSpotifyArtistCode = row.sourceSpotifyArtistCode
    )
}
