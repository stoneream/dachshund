package io.github.stoneream.dachshund.service.application.user_new_release_notification_queue

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.ReleaseNotificationType
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model.UserNewReleaseNotificationQueueTarget

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait UserNewReleaseNotificationQueueService {

  /**
   * 実行可能な queue を取得し、同一 transaction で PROCESSING に遷移させる。
   *
   * 期限切れの PROCESSING queue は claim 前に SCHEDULED へ戻す。
   */
  def claimDueTargets(
      now: BusinessDateTime,
      releaseNotificationType: ReleaseNotificationType,
      batchSize: Int,
      processingLease: FiniteDuration
  ): Future[Seq[UserNewReleaseNotificationQueueTarget]]

  /**
   * playlist への投入成功を queue に反映する。
   */
  def markSucceeded(
      target: UserNewReleaseNotificationQueueTarget,
      spotifySnapshotId: String,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationQueueUpdateResult]

  /**
   * 再試行可能な失敗を記録し、queue を SCHEDULED へ戻す。
   */
  def markTemporaryFailure(
      target: UserNewReleaseNotificationQueueTarget,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationQueueUpdateResult]

  /**
   * ユーザー操作または運用対応が必要な失敗を記録し、queue を BLOCKED にする。
   */
  def markBlocked(
      target: UserNewReleaseNotificationQueueTarget,
      reasonType: String,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationQueueUpdateResult]

  /**
   * abort した通知 run で claim 済みの queue を再実行可能な状態へ戻す。
   */
  def releaseProcessingTargets(
      targets: Seq[UserNewReleaseNotificationQueueTarget],
      now: BusinessDateTime
  ): Future[Int]
}
