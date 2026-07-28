package io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.ReleaseNotificationType
import io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue.model.UserNewReleaseNotificationDeliveryQueueTarget

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait UserNewReleaseNotificationDeliveryQueueService {

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
  ): Future[Seq[UserNewReleaseNotificationDeliveryQueueTarget]]

  /**
   * playlist への投入成功を queue に反映する。
   */
  def markSucceeded(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      spotifySnapshotId: String,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationDeliveryQueueUpdateResult]

  /**
   * 再試行可能な失敗を記録し、queue を SCHEDULED へ戻す。
   */
  def markTemporaryFailure(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationDeliveryQueueUpdateResult]

  /**
   * ユーザー操作または運用対応が必要な失敗を記録し、queue を BLOCKED にする。
   */
  def markBlocked(
      target: UserNewReleaseNotificationDeliveryQueueTarget,
      reasonType: String,
      now: BusinessDateTime
  ): Future[UserNewReleaseNotificationDeliveryQueueUpdateResult]

  /**
   * abort した通知 run で claim 済みの queue を再実行可能な状態へ戻す。
   */
  def releaseProcessingTargets(
      targets: Seq[UserNewReleaseNotificationDeliveryQueueTarget],
      now: BusinessDateTime
  ): Future[Int]
}
