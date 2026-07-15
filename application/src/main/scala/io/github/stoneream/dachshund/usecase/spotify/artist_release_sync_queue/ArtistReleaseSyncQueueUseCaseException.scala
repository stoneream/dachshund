package io.github.stoneream.dachshund.usecase.spotify.artist_release_sync_queue

abstract sealed class ArtistReleaseSyncQueueUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object ArtistReleaseSyncQueueUseCaseException {
  final case class Unexpected(causeException: Throwable) extends ArtistReleaseSyncQueueUseCaseException("アーティストリリース同期キューの作成に失敗しました", causeException)
}
