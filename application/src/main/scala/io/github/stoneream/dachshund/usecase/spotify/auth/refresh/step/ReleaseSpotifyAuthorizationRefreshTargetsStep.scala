package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.SpotifyAuthorizationRefreshQueueWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.SpotifyAuthorizationRefreshTarget

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

/**
 * abort した refresh run で claim 済みの queue を再実行可能な状態へ戻す
 */
@Singleton
private[refresh] class ReleaseSpotifyAuthorizationRefreshTargetsStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    refreshQueueWriter: SpotifyAuthorizationRefreshQueueWriter,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      targets: Seq[SpotifyAuthorizationRefreshTarget],
      now: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        targets.count { target =>
          refreshQueueWriter.update(
            queueId = target.queueId,
            authorizationId = target.authorizationId,
            expectedStatus = target.queueStatus,
            expectedLockToken = target.lockToken,
            expectedQueueLockVersion = target.queueLockVersion,
            expectedDeleted = target.queueDeleted,
            status = QueueJobStatus.Scheduled,
            nextAttemptAt = target.nextAttemptAt,
            attemptCount = target.attemptCount,
            lastFailedAt = target.lastFailedAt,
            lastErrorType = target.lastErrorType,
            lastAttemptedAt = target.lastAttemptedAt,
            completedAt = target.completedAt,
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
    }(using databaseExecutor)
}
