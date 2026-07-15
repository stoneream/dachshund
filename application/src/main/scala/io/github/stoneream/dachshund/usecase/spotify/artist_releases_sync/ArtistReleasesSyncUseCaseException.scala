package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync

abstract sealed class ArtistReleasesSyncUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object ArtistReleasesSyncUseCaseException {
  final case class TargetClaimFailed(queueId: Long) extends ArtistReleasesSyncUseCaseException("アーティストリリース同期キューの claim に失敗しました")

  final case class Unexpected(causeException: Throwable) extends ArtistReleasesSyncUseCaseException("アーティストリリース同期に失敗しました", causeException)
}
