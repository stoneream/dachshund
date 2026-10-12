package io.github.stoneream.dachshund.usecase.spotify.track.detail

import io.github.stoneream.dachshund.auth.UserSessionContext

import scala.concurrent.duration.FiniteDuration

sealed abstract class SpotifyTrackDetailUseCaseException(
    message: String,
    cause: Throwable = null
) extends Exception(message, cause)

object SpotifyTrackDetailUseCaseException {
  case object AuthorizationRequired extends SpotifyTrackDetailUseCaseException("Spotify の再認可が必要です")

  final case class TrackNotFound(user: UserSessionContext.NormalUser) extends SpotifyTrackDetailUseCaseException("楽曲が見つかりませんでした")

  final case class Forbidden(user: UserSessionContext.NormalUser) extends SpotifyTrackDetailUseCaseException("楽曲の取得が許可されていません")

  final case class RateLimited(
      user: UserSessionContext.NormalUser,
      retryAfter: Option[FiniteDuration],
      causeException: Throwable
  ) extends SpotifyTrackDetailUseCaseException("楽曲情報の取得が rate limit に達しました", causeException)

  final case class Unavailable(user: UserSessionContext.NormalUser, causeException: Throwable)
      extends SpotifyTrackDetailUseCaseException("楽曲情報を取得できませんでした", causeException)
}
