package io.github.stoneream.dachshund.usecase.spotify.artist_release_sync_queue.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseSyncQueueSource
import io.github.stoneream.dachshund.infra.db.reader.artist_release_sync_queue.ArtistReleaseSyncQueueReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.ArtistReleaseSyncQueueWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget

import scala.concurrent.Future

@Singleton
private[artist_release_sync_queue] class CreateArtistReleaseSyncQueuesStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    queueReader: ArtistReleaseSyncQueueReader,
    queueWriter: ArtistReleaseSyncQueueWriter,
    databaseExecutor: DatabaseExecutor
) {
  private val SyncScopeIncremental: String = "INCREMENTAL"
  private val IncludeGroupsAlbumSingle: String = "album,single"
  // Spotify Get Artist's Albums API の limit 上限
  // https://developer.spotify.com/documentation/web-api/reference/get-an-artists-albums
  private val RequestedLimit: Int = 10

  def run(
      now: BusinessDateTime
  ): Future[Int] = {
    val rescheduleSucceededBefore = startOfBusinessDate(now)

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val artistCodes = queueReader.findActiveFollowedArtistCodes()
        val existingQueues = queueReader.findQueuesForActiveFollowedArtists(SyncScopeIncremental)
        val existingQueuesByArtistCode = existingQueues.map(queue => queue.spotifyArtistCode -> queue).toMap

        val createdCount = artistCodes
          .filterNot(existingQueuesByArtistCode.contains)
          .map { spotifyArtistCode =>
            queueWriter.write(
              ArtistReleaseSyncQueueSource(
                spotifyArtistCode = spotifyArtistCode,
                syncScope = SyncScopeIncremental,
                status = QueueJobStatus.Scheduled,
                includeGroups = IncludeGroupsAlbumSingle,
                market = Option.empty,
                requestedLimit = RequestedLimit,
                nextOffset = 0,
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
              ).toArtistReleaseSyncQueueDbRow
            )
          }
          .sum

        val rescheduledCount = existingQueues
          .filter(reusableQueue(_, rescheduleSucceededBefore))
          .count { queue =>
            queueWriter.update(
              queueId = queue.queueId,
              spotifyArtistCode = queue.spotifyArtistCode,
              syncScope = queue.syncScope,
              expectedStatus = queue.status,
              expectedLockToken = queue.lockToken,
              expectedQueueLockVersion = queue.queueLockVersion,
              expectedDeleted = queue.deleted,
              status = QueueJobStatus.Scheduled,
              includeGroups = IncludeGroupsAlbumSingle,
              market = Option.empty,
              requestedLimit = RequestedLimit,
              nextOffset = 0,
              nextAttemptAt = Some(now),
              lastAttemptedAt = Option.empty,
              completedAt = Option.empty,
              attemptCount = 0,
              lastFailedAt = Option.empty,
              lastErrorType = "",
              lockToken = "",
              lockedUntil = Option.empty,
              updatedAt = now,
              deletedAt = Option.empty,
              updatedUser = AuditUser.System,
              deletedUser = AuditUser.Empty,
              deleted = 0L,
              lockVersion = queue.queueLockVersion + 1L
            )
          }

        createdCount + rescheduledCount
      }
    }(using databaseExecutor)
  }

  private def reusableQueue(
      queue: ArtistReleaseSyncQueueTarget,
      rescheduleSucceededBefore: BusinessDateTime
  ): Boolean =
    queue.deleted != 0 ||
      queue.status == QueueJobStatus.Failed ||
      queue.status == QueueJobStatus.Skipped ||
      (queue.status == QueueJobStatus.Succeeded && queue.completedAt.exists(_.isBefore(rescheduleSucceededBefore)))

  private def startOfBusinessDate(now: BusinessDateTime): BusinessDateTime =
    BusinessDateTime.from(
      now.asOffsetDateTime.toLocalDate.atStartOfDay().atOffset(now.asOffsetDateTime.getOffset)
    )
}
