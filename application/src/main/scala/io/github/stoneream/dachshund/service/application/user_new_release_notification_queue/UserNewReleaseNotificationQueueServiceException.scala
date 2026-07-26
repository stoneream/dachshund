package io.github.stoneream.dachshund.service.application.user_new_release_notification_queue

abstract sealed class UserNewReleaseNotificationQueueServiceException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object UserNewReleaseNotificationQueueServiceException {
  final case class TargetClaimFailed(queueId: Long) extends UserNewReleaseNotificationQueueServiceException("ユーザー別新着リリース通知キューの claim に失敗しました")
}
