package io.github.stoneream.dachshund.service.application.followed_artists_sync_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.model.QueueJobStatus
import FollowedArtistSyncQueueUpdateResult.{StaleLockSkipped, Updated}

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.followed_artists_sync_queue.FollowedArtistSyncQueueReader
import io.github.stoneream.dachshund.infra.db.writer.FollowedArtistSyncQueueWriter
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.FollowedArtistSyncQueueServiceException as ServiceException
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget
import FollowedArtistSyncQueueProgressResult as ProgressResult
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@Singleton
class FollowedArtistSyncQueueServiceImpl @Inject() (
    databaseTransaction: DatabaseTransaction,
    queueReader: FollowedArtistSyncQueueReader,
    queueWriter: FollowedArtistSyncQueueWriter,
    databaseExecutor: DatabaseExecutor
) extends FollowedArtistSyncQueueService {
  override def claimDueTargets(
      now: BusinessDateTime,
      batchSize: Int,
      processingLease: FiniteDuration
  ): Future[Seq[FollowedArtistSyncQueueTarget]] =
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
      target: FollowedArtistSyncQueueTarget,
      nextAfterCursor: Option[String],
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueUpdateResult] = {
    val completed = nextAfterCursor.isEmpty

    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        userId = target.userId,
        syncDate = target.syncDate,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = if (completed) QueueJobStatus.Succeeded else QueueJobStatus.Scheduled,
        requestedLimit = target.requestedLimit,
        afterCursor = nextAfterCursor,
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

  override def markPageProgressed(
      target: FollowedArtistSyncQueueTarget,
      nextAfterCursor: String,
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueProgressResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val updated = queueWriter.update(
          queueId = target.queueId,
          userId = target.userId,
          syncDate = target.syncDate,
          expectedStatus = QueueJobStatus.Processing,
          expectedLockToken = target.lockToken,
          expectedQueueLockVersion = target.queueLockVersion,
          expectedDeleted = target.deleted,
          status = QueueJobStatus.Processing,
          requestedLimit = target.requestedLimit,
          afterCursor = Some(nextAfterCursor),
          nextAttemptAt = target.nextAttemptAt,
          lastAttemptedAt = target.lastAttemptedAt,
          completedAt = target.completedAt,
          attemptCount = target.attemptCount,
          lastFailedAt = target.lastFailedAt,
          lastErrorType = target.lastErrorType,
          lockToken = target.lockToken,
          lockedUntil = target.lockedUntil,
          updatedAt = now,
          deletedAt = target.deletedAt,
          updatedUser = AuditUser.System,
          deletedUser = target.deletedUser,
          deleted = target.deleted,
          lockVersion = target.queueLockVersion + 1L
        )

        if (updated) {
          ProgressResult.Updated(
            target.copy(
              afterCursor = Some(nextAfterCursor),
              queueLockVersion = target.queueLockVersion + 1L
            )
          )
        } else {
          ProgressResult.StaleLockSkipped
        }
      }
    }(using databaseExecutor)

  override def markTemporaryFailure(
      target: FollowedArtistSyncQueueTarget,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueUpdateResult] = {
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        userId = target.userId,
        syncDate = target.syncDate,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = QueueJobStatus.Scheduled,
        requestedLimit = target.requestedLimit,
        afterCursor = target.afterCursor,
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
      target: FollowedArtistSyncQueueTarget,
      reasonType: String,
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueUpdateResult] = {
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        userId = target.userId,
        syncDate = target.syncDate,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = QueueJobStatus.Blocked,
        requestedLimit = target.requestedLimit,
        afterCursor = target.afterCursor,
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
      targets: Seq[FollowedArtistSyncQueueTarget],
      now: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        targets.count { target =>
          queueWriter.update(
            queueId = target.queueId,
            userId = target.userId,
            syncDate = target.syncDate,
            expectedStatus = QueueJobStatus.Processing,
            expectedLockToken = target.lockToken,
            expectedQueueLockVersion = target.queueLockVersion,
            expectedDeleted = target.deleted,
            status = QueueJobStatus.Scheduled,
            requestedLimit = target.requestedLimit,
            afterCursor = target.afterCursor,
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
  ): Future[FollowedArtistSyncQueueUpdateResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        if (update) Updated else StaleLockSkipped
      }
    }(using databaseExecutor)
}
