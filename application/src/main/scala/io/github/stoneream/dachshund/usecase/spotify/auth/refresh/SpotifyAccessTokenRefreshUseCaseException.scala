package io.github.stoneream.dachshund.usecase.spotify.auth.refresh

abstract sealed class SpotifyAccessTokenRefreshUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object SpotifyAccessTokenRefreshUseCaseException {
  final case class InvalidClientCredentials(causeException: Throwable)
      extends SpotifyAccessTokenRefreshUseCaseException("Spotify client credentials の検証に失敗しました", causeException)

  final case class RefreshTargetClaimFailed(queueId: Long) extends SpotifyAccessTokenRefreshUseCaseException("Spotify 認可更新キューの claim に失敗しました")

  final case class Unknown(causeException: Throwable)
      extends SpotifyAccessTokenRefreshUseCaseException("Spotify access token refresh daemon が失敗しました", causeException)
}
