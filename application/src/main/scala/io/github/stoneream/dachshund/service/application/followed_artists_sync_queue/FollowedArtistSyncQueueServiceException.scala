package io.github.stoneream.dachshund.service.application.followed_artists_sync_queue

abstract sealed class FollowedArtistSyncQueueServiceException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object FollowedArtistSyncQueueServiceException {
  final case class TargetClaimFailed(queueId: Long) extends FollowedArtistSyncQueueServiceException("フォロー中アーティスト同期キューの claim に失敗しました")
}
