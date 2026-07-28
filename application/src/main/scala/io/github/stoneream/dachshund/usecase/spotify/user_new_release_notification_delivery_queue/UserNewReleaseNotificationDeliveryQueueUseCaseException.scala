package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery_queue

abstract sealed class UserNewReleaseNotificationDeliveryQueueUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object UserNewReleaseNotificationDeliveryQueueUseCaseException {
  final case class TargetClaimFailed(queueId: Long) extends UserNewReleaseNotificationDeliveryQueueUseCaseException("ユーザー別新着リリース通知配信キューの claim に失敗しました")
}
