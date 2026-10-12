package io.github.stoneream.dachshund.handler.spotify.track.detail

import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.spotify.track.detail.SpotifyTrackDetailUseCaseOutput.AudioFeaturesState
import io.github.stoneream.dachshund.usecase.spotify.track.detail.{SpotifyTrackDetailUseCaseException as UseCaseException, SpotifyTrackDetailUseCaseOutput as UseCaseOutput}
import play.api.mvc.{Result, Results}

import com.google.inject.Singleton

@Singleton
class SpotifyTrackDetailRenderer extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
  private val pageMeta = PageMeta(title = "楽曲詳細 | Dachshund")

  override def success(output: UseCaseOutput): Result = {
    val (features, notice) = output.audioFeatures match {
      case AudioFeaturesState.Available(features) => (Some(features), None)
      case AudioFeaturesState.AccessDenied =>
        (None, Some("ReccoBeats によって音響指標へのアクセスが許可されていません。"))
      case AudioFeaturesState.NotFound =>
        (None, Some("この楽曲の音響指標が見つかりませんでした。"))
    }
    render(pageMeta)(
      views.html.spotify.trackDetail(
        output.user.displayName,
        Some(output.track),
        TrackDetailPresentation.metrics(output.track, features),
        notice,
        None
      )
    )
  }

  override def failure(exception: UseCaseException): Result = exception match {
    case UseCaseException.AuthorizationRequired =>
      Results.SeeOther("/spotify/auth/login").withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
    case failure: UseCaseException.TrackNotFound =>
      error(Results.NotFound, failure.user.displayName, "楽曲が見つかりませんでした。検索から別の楽曲を選択してください。")
    case failure: UseCaseException.Forbidden =>
      error(Results.Forbidden, failure.user.displayName, "Spotify によってこの楽曲へのアクセスが許可されていません。")
    case failure: UseCaseException.RateLimited =>
      val retryAfterSeconds = failure.retryAfter.map(delay => math.max(1L, (delay.toMillis + 999L) / 1000L))
      val guidance = retryAfterSeconds.fold("時間をおいて再度お試しください。")(seconds => s"${seconds}秒後に再度お試しください。")
      val result = error(Results.TooManyRequests, failure.user.displayName, s"楽曲情報の取得先が混み合っています。$guidance")
      retryAfterSeconds.fold(result) { seconds =>
        result.withHeaders("Retry-After" -> seconds.toString)
      }
    case failure: UseCaseException.Unavailable =>
      error(Results.ServiceUnavailable, failure.user.displayName, "楽曲情報を取得できませんでした。時間をおいて再度お試しください。")
  }

  private def error(status: Results.Status, displayName: String, message: String): Result =
    render(status, pageMeta)(views.html.spotify.trackDetail(displayName, None, Seq.empty, None, Some(message)))
}
