package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync_queue

abstract sealed class FollowedArtistsSyncQueueUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object FollowedArtistsSyncQueueUseCaseException {
  final case class Unexpected(causeException: Throwable) extends FollowedArtistsSyncQueueUseCaseException("フォロー中アーティスト同期キューの作成に失敗しました", causeException)
}
