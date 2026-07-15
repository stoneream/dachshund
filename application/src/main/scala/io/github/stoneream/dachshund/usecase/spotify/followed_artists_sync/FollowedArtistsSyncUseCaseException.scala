package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync

abstract sealed class FollowedArtistsSyncUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object FollowedArtistsSyncUseCaseException {
  final case class TargetClaimFailed(queueId: Long) extends FollowedArtistsSyncUseCaseException("フォロー中アーティスト同期キューの claim に失敗しました")

  final case class Unexpected(causeException: Throwable) extends FollowedArtistsSyncUseCaseException("フォロー中アーティスト同期 daemon が失敗しました", causeException)
}
