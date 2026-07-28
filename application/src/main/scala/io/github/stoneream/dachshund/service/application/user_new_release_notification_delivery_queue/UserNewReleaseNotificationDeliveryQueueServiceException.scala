package io.github.stoneream.dachshund.service.application.user_new_release_notification_delivery_queue

abstract sealed class UserNewReleaseNotificationDeliveryQueueServiceException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object UserNewReleaseNotificationDeliveryQueueServiceException {
  final case class TargetClaimFailed(queueId: Long) extends UserNewReleaseNotificationDeliveryQueueServiceException("ユーザー別新着リリース通知配信キューの claim に失敗しました")
}
