package io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseNotificationDeliveryQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseEventDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{UserNewReleaseEventSource, UserNewReleaseNotificationDeliveryQueueSource}
import io.github.stoneream.dachshund.infra.db.reader.user_playlist_setting.UserPlaylistSettingReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.{UserNewReleaseEventsWriter, UserNewReleaseNotificationDeliveryQueueWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.model.{PlaylistUsageType, QueueJobStatus, ReleaseNotificationType}
import io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.step.WriteMissingUserNewReleaseEventsStep.Result

import scala.concurrent.Future

private[user_new_release_events_sync] object WriteMissingUserNewReleaseEventsStep {
  final case class Result(
      createdCount: Int,
      notificationQueueCreatedCount: Int
  )
}

@Singleton
private[user_new_release_events_sync] class WriteMissingUserNewReleaseEventsStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userNewReleaseEventsWriter: UserNewReleaseEventsWriter,
    userPlaylistSettingReader: UserPlaylistSettingReader,
    userNewReleaseNotificationDeliveryQueueWriter: UserNewReleaseNotificationDeliveryQueueWriter,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      targets: Seq[UserNewReleaseEventSource],
      detectionSyncCode: String,
      detectedAt: BusinessDateTime
  ): Future[Result] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        targets.foldLeft(Result(createdCount = 0, notificationQueueCreatedCount = 0)) { (result, target) =>
          val eventId = userNewReleaseEventsWriter.writeIfAbsentReturningId(
            target
              .copy(
                detectedAt = detectedAt,
                detectionSyncCode = detectionSyncCode,
                createdAt = detectedAt,
                updatedAt = detectedAt,
                createdUser = AuditUser.System,
                updatedUser = AuditUser.System
              )
              .toUserNewReleaseEventDbRow
          )
          val notificationQueueCreatedCount = eventId
            .map { id =>
              createPlaylistNotificationQueue(target, id, detectedAt)
            }
            .getOrElse(0)

          result.copy(
            createdCount = result.createdCount + eventId.size,
            notificationQueueCreatedCount = result.notificationQueueCreatedCount + notificationQueueCreatedCount
          )
        }
      }
    }(using databaseExecutor)

  private def createPlaylistNotificationQueue(
      target: UserNewReleaseEventSource,
      userNewReleaseEventId: Long,
      now: BusinessDateTime
  )(using scalikejdbc.DBSession): Int =
    userPlaylistSettingReader
      .findEnabled(
        userId = target.userId,
        playlistUsageType = PlaylistUsageType.NewReleaseNotification
      )
      .map { playlistSetting =>
        userNewReleaseNotificationDeliveryQueueWriter.write(
          UserNewReleaseNotificationDeliveryQueueSource(
            userNewReleaseEventId = userNewReleaseEventId,
            releaseNotificationType = ReleaseNotificationType.Playlist,
            playlistSettingId = playlistSetting.id,
            status = QueueJobStatus.Scheduled,
            nextAttemptAt = Some(now),
            attemptCount = 0,
            lastFailedAt = Option.empty,
            lastErrorType = "",
            lockToken = "",
            lockedUntil = Option.empty,
            lastAttemptedAt = Option.empty,
            completedAt = Option.empty,
            spotifySnapshotId = "",
            createdAt = now,
            updatedAt = now,
            deletedAt = Option.empty,
            createdUser = AuditUser.System,
            updatedUser = AuditUser.System,
            deletedUser = AuditUser.Empty,
            deleted = 0L,
            lockVersion = 0L
          ).toUserNewReleaseNotificationDeliveryQueueDbRow
        )
      }
      .getOrElse(0)
}
