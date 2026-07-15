package io.github.stoneream.dachshund.service.application.artist_release_sync_queue

abstract sealed class ArtistReleaseSyncQueueServiceException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object ArtistReleaseSyncQueueServiceException {
  final case class TargetClaimFailed(queueId: Long) extends ArtistReleaseSyncQueueServiceException("アーティストリリース同期キューの claim に失敗しました")
}
