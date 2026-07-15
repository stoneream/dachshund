package io.github.stoneream.dachshund.service.application.artist_release_sync_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.reader.artist_release_sync_queue.ArtistReleaseSyncQueueReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.ArtistReleaseSyncQueueWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.model.QueueJobStatus
import ArtistReleaseSyncQueueUpdateResult.{StaleLockSkipped, Updated}

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.ArtistReleaseSyncQueueServiceException as ServiceException
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@Singleton
class ArtistReleaseSyncQueueServiceImpl @Inject() (
    databaseTransaction: DatabaseTransaction,
    queueReader: ArtistReleaseSyncQueueReader,
    queueWriter: ArtistReleaseSyncQueueWriter,
    databaseExecutor: DatabaseExecutor
) extends ArtistReleaseSyncQueueService {
  override def claimDueTargets(
      now: BusinessDateTime,
      batchSize: Int,
      processingLease: FiniteDuration
  ): Future[Seq[ArtistReleaseSyncQueueTarget]] =
    Future {
      val lockToken = UUID.randomUUID().toString
      val lockedUntil = now.plus(processingLease)

      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        queueReader.recoverStaleProcessingTargets(now = now)

        val claimResults = queueReader.claimDueTargets(
          now = now,
          batchSize = batchSize,
          lockToken = lockToken,
          lockedUntil = lockedUntil
        )
        claimResults.find(!_.claimed).foreach { result =>
          throw ServiceException.TargetClaimFailed(result.target.queueId)
        }

        claimResults.map(_.target)
      }
    }(using databaseExecutor)

  override def markPageProcessed(
      target: ArtistReleaseSyncQueueTarget,
      nextOffset: Int,
      completed: Boolean,
      now: BusinessDateTime
  ): Future[ArtistReleaseSyncQueueUpdateResult] = {
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        spotifyArtistCode = target.spotifyArtistCode,
        syncScope = target.syncScope,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = if (completed) QueueJobStatus.Succeeded else QueueJobStatus.Scheduled,
        includeGroups = target.includeGroups,
        market = target.market,
        requestedLimit = target.requestedLimit,
        nextOffset = nextOffset,
        nextAttemptAt = if (completed) Option.empty else Some(now),
        lastAttemptedAt = target.lastAttemptedAt,
        completedAt = if (completed) Some(now) else Option.empty,
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
        lockVersion = target.queueLockVersion + 1L
      )
    }
  }

  override def markTemporaryFailure(
      target: ArtistReleaseSyncQueueTarget,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  ): Future[ArtistReleaseSyncQueueUpdateResult] = {
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        spotifyArtistCode = target.spotifyArtistCode,
        syncScope = target.syncScope,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = QueueJobStatus.Scheduled,
        includeGroups = target.includeGroups,
        market = target.market,
        requestedLimit = target.requestedLimit,
        nextOffset = target.nextOffset,
        nextAttemptAt = Some(nextAttemptAt),
        lastAttemptedAt = target.lastAttemptedAt,
        completedAt = Option.empty,
        attemptCount = target.attemptCount,
        lastFailedAt = Some(now),
        lastErrorType = failureType,
        lockToken = "",
        lockedUntil = Option.empty,
        updatedAt = now,
        deletedAt = Option.empty,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = target.queueLockVersion + 1L
      )
    }
  }

  override def markBlocked(
      target: ArtistReleaseSyncQueueTarget,
      reasonType: String,
      now: BusinessDateTime
  ): Future[ArtistReleaseSyncQueueUpdateResult] = {
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        spotifyArtistCode = target.spotifyArtistCode,
        syncScope = target.syncScope,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = QueueJobStatus.Blocked,
        includeGroups = target.includeGroups,
        market = target.market,
        requestedLimit = target.requestedLimit,
        nextOffset = target.nextOffset,
        nextAttemptAt = Option.empty,
        lastAttemptedAt = target.lastAttemptedAt,
        completedAt = Option.empty,
        attemptCount = target.attemptCount,
        lastFailedAt = Some(now),
        lastErrorType = reasonType,
        lockToken = "",
        lockedUntil = Option.empty,
        updatedAt = now,
        deletedAt = Option.empty,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = target.queueLockVersion + 1L
      )
    }
  }

  override def releaseProcessingTargets(
      targets: Seq[ArtistReleaseSyncQueueTarget],
      now: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        targets.count { target =>
          queueWriter.update(
            queueId = target.queueId,
            spotifyArtistCode = target.spotifyArtistCode,
            syncScope = target.syncScope,
            expectedStatus = QueueJobStatus.Processing,
            expectedLockToken = target.lockToken,
            expectedQueueLockVersion = target.queueLockVersion,
            expectedDeleted = target.deleted,
            status = QueueJobStatus.Scheduled,
            includeGroups = target.includeGroups,
            market = target.market,
            requestedLimit = target.requestedLimit,
            nextOffset = target.nextOffset,
            nextAttemptAt = target.nextAttemptAt,
            lastAttemptedAt = target.lastAttemptedAt,
            completedAt = target.completedAt,
            attemptCount = target.attemptCount,
            lastFailedAt = target.lastFailedAt,
            lastErrorType = target.lastErrorType,
            lockToken = "",
            lockedUntil = Option.empty,
            updatedAt = now,
            deletedAt = target.deletedAt,
            updatedUser = AuditUser.System,
            deletedUser = target.deletedUser,
            deleted = target.deleted,
            lockVersion = target.queueLockVersion + 1L
          )
        }
      }
    }(using databaseExecutor)

  private def updateResult(
      update: scalikejdbc.DBSession ?=> Boolean
  ): Future[ArtistReleaseSyncQueueUpdateResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        if (update) Updated else StaleLockSkipped
      }
    }(using databaseExecutor)
}
