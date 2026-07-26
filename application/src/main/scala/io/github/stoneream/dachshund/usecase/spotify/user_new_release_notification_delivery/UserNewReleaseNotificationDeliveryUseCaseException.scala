package io.github.stoneream.dachshund.usecase.spotify.user_new_release_notification_delivery

abstract sealed class UserNewReleaseNotificationDeliveryUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object UserNewReleaseNotificationDeliveryUseCaseException {
  final case class TargetClaimFailed(queueId: Long) extends UserNewReleaseNotificationDeliveryUseCaseException("ユーザー別新着リリース通知キューの claim に失敗しました")

  final case class Unexpected(causeException: Throwable)
      extends UserNewReleaseNotificationDeliveryUseCaseException("ユーザー別新着リリース通知配信 daemon が失敗しました", causeException)
}
