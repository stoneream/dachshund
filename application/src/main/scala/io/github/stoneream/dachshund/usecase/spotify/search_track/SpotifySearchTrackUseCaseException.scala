package io.github.stoneream.dachshund.usecase.spotify.search_track

import io.github.stoneream.dachshund.auth.UserSessionContext

import scala.concurrent.duration.FiniteDuration

sealed abstract class SpotifySearchTrackUseCaseException(
    message: String,
    cause: Throwable = null
) extends Exception(message, cause)

object SpotifySearchTrackUseCaseException {
  case object AuthorizationRequired extends SpotifySearchTrackUseCaseException("Spotify の再認可が必要です")

  final case class RateLimited(
      user: UserSessionContext.NormalUser,
      query: String,
      retryAfter: Option[FiniteDuration],
      causeException: Throwable
  ) extends SpotifySearchTrackUseCaseException("Spotify の検索が rate limit に達しました", causeException)

  final case class Unavailable(
      user: UserSessionContext.NormalUser,
      query: String,
      causeException: Throwable
  ) extends SpotifySearchTrackUseCaseException("楽曲を検索できませんでした", causeException)
}
