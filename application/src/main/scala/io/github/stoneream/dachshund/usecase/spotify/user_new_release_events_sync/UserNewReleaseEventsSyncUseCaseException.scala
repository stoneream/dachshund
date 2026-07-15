package io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync

abstract sealed class UserNewReleaseEventsSyncUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object UserNewReleaseEventsSyncUseCaseException {
  final case class Unexpected(causeException: Throwable) extends UserNewReleaseEventsSyncUseCaseException("ユーザー別新着リリース履歴の作成に失敗しました", causeException)
}
