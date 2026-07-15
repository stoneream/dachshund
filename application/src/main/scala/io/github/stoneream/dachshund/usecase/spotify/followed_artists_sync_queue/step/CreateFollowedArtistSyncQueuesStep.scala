package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync_queue.step

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.FollowedArtistSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.FollowedArtistSyncQueueSource
import io.github.stoneream.dachshund.infra.db.reader.followed_artists_sync_queue.FollowedArtistSyncQueueReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.FollowedArtistSyncQueueWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.model.QueueJobStatus

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
private[followed_artists_sync_queue] class CreateFollowedArtistSyncQueuesStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    queueReader: FollowedArtistSyncQueueReader,
    queueWriter: FollowedArtistSyncQueueWriter,
    databaseExecutor: DatabaseExecutor
) {
  // Spotify Get Followed Artists API の limit 上限
  // https://developer.spotify.com/documentation/web-api/reference/get-followed
  private val RequestedLimit: Int = 50

  def run(
      now: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val syncDate = now.toLocalDate
        val activeUserIds = queueReader.findActiveUserIds()
        val queuedUserIds = queueReader.findQueuesForActiveUsers(syncDate).map(_.userId).toSet

        activeUserIds
          .filterNot(queuedUserIds.contains)
          .map { userId =>
            queueWriter.write(
              FollowedArtistSyncQueueSource(
                userId = userId,
                syncDate = syncDate,
                status = QueueJobStatus.Scheduled,
                requestedLimit = RequestedLimit,
                afterCursor = Option.empty,
                nextAttemptAt = Some(now),
                lastAttemptedAt = Option.empty,
                completedAt = Option.empty,
                attemptCount = 0,
                lastFailedAt = Option.empty,
                lastErrorType = "",
                lockToken = "",
                lockedUntil = Option.empty,
                createdAt = now,
                updatedAt = now,
                deletedAt = Option.empty,
                createdUser = AuditUser.System,
                updatedUser = AuditUser.System,
                deletedUser = AuditUser.Empty,
                deleted = 0L,
                lockVersion = 0L
              ).toFollowedArtistSyncQueueDbRow
            )
          }
          .sum
      }
    }(using databaseExecutor)
}
