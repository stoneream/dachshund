package io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.reader.user_new_release_notification_delivery_queue.UserNewReleaseNotificationDeliveryQueueReader
import io.github.stoneream.dachshund.infra.db.reader.user_new_release_notification_delivery_queue.UserNewReleaseNotificationDeliveryQueueReader.QueueTarget
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.UserNewReleaseNotificationDeliveryQueueWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.model.{QueueJobStatus, ReleaseNotificationType}
import UserNewReleaseNotificationDeliveryQueueUpdateResult.{StaleLockSkipped, Updated}

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.UserNewReleaseNotificationDeliveryQueueServiceException as ServiceException
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.model.UserNewReleaseNotificationDeliveryQueueTarget
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import java.util.UUID

@Singleton
class UserNewReleaseNotificationDeliveryQueueServiceImpl @Inject() (
    databaseTransaction: DatabaseTransaction,
    queueReader: UserNewReleaseNotificationDeliveryQueueReader,
    queueWriter: UserNewReleaseNotificationDeliveryQueueWriter,
    databaseExecutor: DatabaseExecutor
) extends UserNewReleaseNotificationDeliveryQueueService {
  override def claimDueTargets(
      now: BusinessDateTime,
      releaseNotificationType: ReleaseNotificationType,
      batchSize: Int,
      processingLease: FiniteDuration
  ): Future[Seq[UserNewReleaseNotificationDeliveryQueueTarget]] =
    Future {
      val lockToken = UUID.randomUUID().toString
      val lockedUntil = now.plus(processingLease)

      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        queueReader.recoverStaleProcessingTargets(
          now = now,
          releaseNotificationType = releaseNotificationType
        )

        val claimResults = queueReader.claimDueTargets(
          now = now,
          releaseNotificationType = releaseNotificationType,
          batchSize = batchSize,
          lockToken = lockToken,
          lockedUntil = lockedUntil
        )
        claimResults.find(!_.claimed) match {
          case Some(result) =>
            throw ServiceException.TargetClaimFailed(result.target.queue.id)
          case None =>
            claimResults.map(result => toTarget(result.target))
        }
      }
    }(using databaseExecutor)

  override def markSucceeded(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      spotifySnapshotId: String,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationDeliveryQueueUpdateResult] =
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        userNewReleaseEventId = target.userNewReleaseEventId,
        releaseNotificationType = target.releaseNotificationType,
        playlistSettingId = target.playlistSettingId,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = QueueJobStatus.Succeeded,
        nextAttemptAt = Option.empty,
        attemptCount = 0,
        lastFailedAt = Option.empty,
        lastErrorType = "",
        lockToken = "",
        lockedUntil = Option.empty,
        lastAttemptedAt = target.lastAttemptedAt,
        completedAt = Some(now),
        spotifySnapshotId = spotifySnapshotId,
        updatedAt = now,
        deletedAt = Option.empty,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = target.queueLockVersion + 1L
      )
    }

  override def markTemporaryFailure(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationDeliveryQueueUpdateResult] =
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        userNewReleaseEventId = target.userNewReleaseEventId,
        releaseNotificationType = target.releaseNotificationType,
        playlistSettingId = target.playlistSettingId,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = QueueJobStatus.Scheduled,
        nextAttemptAt = Some(nextAttemptAt),
        attemptCount = target.attemptCount,
        lastFailedAt = Some(now),
        lastErrorType = failureType,
        lockToken = "",
        lockedUntil = Option.empty,
        lastAttemptedAt = target.lastAttemptedAt,
        completedAt = Option.empty,
        spotifySnapshotId = target.spotifySnapshotId,
        updatedAt = now,
        deletedAt = Option.empty,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = target.queueLockVersion + 1L
      )
    }

  override def markBlocked(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      reasonType: String,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationDeliveryQueueUpdateResult] =
    updateResult {
      queueWriter.update(
        queueId = target.queueId,
        userNewReleaseEventId = target.userNewReleaseEventId,
        releaseNotificationType = target.releaseNotificationType,
        playlistSettingId = target.playlistSettingId,
        expectedStatus = QueueJobStatus.Processing,
        expectedLockToken = target.lockToken,
        expectedQueueLockVersion = target.queueLockVersion,
        expectedDeleted = target.deleted,
        status = QueueJobStatus.Blocked,
        nextAttemptAt = Option.empty,
        attemptCount = target.attemptCount,
        lastFailedAt = Some(now),
        lastErrorType = reasonType,
        lockToken = "",
        lockedUntil = Option.empty,
        lastAttemptedAt = target.lastAttemptedAt,
        completedAt = Option.empty,
        spotifySnapshotId = target.spotifySnapshotId,
        updatedAt = now,
        deletedAt = Option.empty,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = target.queueLockVersion + 1L
      )
    }

  override def releaseProcessingTargets(
      targets: Seq[UserNewReleaseNotificationDeliveryQueueTarget],
      now: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        targets.count { target =>
          queueWriter.update(
            queueId = target.queueId,
            userNewReleaseEventId = target.userNewReleaseEventId,
            releaseNotificationType = target.releaseNotificationType,
            playlistSettingId = target.playlistSettingId,
            expectedStatus = QueueJobStatus.Processing,
            expectedLockToken = target.lockToken,
            expectedQueueLockVersion = target.queueLockVersion,
            expectedDeleted = target.deleted,
            status = QueueJobStatus.Scheduled,
            nextAttemptAt = target.nextAttemptAt,
            attemptCount = target.attemptCount,
            lastFailedAt = target.lastFailedAt,
            lastErrorType = target.lastErrorType,
            lockToken = "",
            lockedUntil = Option.empty,
            lastAttemptedAt = target.lastAttemptedAt,
            completedAt = target.completedAt,
            spotifySnapshotId = target.spotifySnapshotId,
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
  ): Future[UserNewReleaseNotificationDeliveryQueueUpdateResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        if (update) Updated else StaleLockSkipped
      }
    }(using databaseExecutor)

  private def toTarget(target: QueueTarget): UserNewReleaseNotificationDeliveryQueueTarget = {
    val queue = target.queue

    UserNewReleaseNotificationDeliveryQueueTarget(
      queueId = queue.id,
      userNewReleaseEventId = queue.userNewReleaseEventId,
      userId = target.userId,
      artistReleaseId = target.artistReleaseId,
      spotifyReleaseCode = target.spotifyReleaseCode,
      releaseNotificationType = queue.releaseNotificationType,
      playlistSettingId = queue.playlistSettingId,
      spotifyPlaylistCode = target.spotifyPlaylistCode,
      status = queue.status,
      nextAttemptAt = queue.nextAttemptAt,
      attemptCount = queue.attemptCount,
      lastFailedAt = queue.lastFailedAt,
      lastErrorType = queue.lastErrorType,
      lockToken = queue.lockToken,
      lockedUntil = queue.lockedUntil,
      lastAttemptedAt = queue.lastAttemptedAt,
      completedAt = queue.completedAt,
      spotifySnapshotId = queue.spotifySnapshotId,
      deletedAt = queue.deletedAt,
      deletedUser = queue.deletedUser,
      deleted = queue.deleted,
      queueLockVersion = queue.lockVersion
    )
  }
}
