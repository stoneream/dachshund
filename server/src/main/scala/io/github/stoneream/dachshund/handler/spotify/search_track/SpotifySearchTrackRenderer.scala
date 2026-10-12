package io.github.stoneream.dachshund.handler.spotify.search_track

import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.spotify.search_track.SpotifySearchTrackUseCaseOutput.SearchState
import io.github.stoneream.dachshund.usecase.spotify.search_track.{SpotifySearchTrackUseCaseException as UseCaseException, SpotifySearchTrackUseCaseOutput as UseCaseOutput}
import play.api.mvc.{Result, Results}

import com.google.inject.Singleton

@Singleton
class SpotifySearchTrackRenderer extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
  private val pageMeta = PageMeta(title = "Search tracks | Dachshund")

  override def success(output: UseCaseOutput): Result = {
    val page = output.state match {
      case SearchState.Initial => None
      case SearchState.Results(page) => Some(page)
    }
    render(pageMeta)(
      views.html.spotify.searchTrack(output.user.displayName, output.query, page, None)
    )
  }

  override def failure(exception: UseCaseException): Result = exception match {
    case UseCaseException.AuthorizationRequired =>
      Results
        .SeeOther("/spotify/auth/login")
        .withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
    case failure: UseCaseException.RateLimited =>
      val result = render(Results.TooManyRequests, pageMeta)(
        views.html.spotify.searchTrack(
          failure.user.displayName,
          failure.query,
          None,
          Some("Spotify の検索が混み合っています。時間をおいて再度検索してください。")
        )
      )
      failure.retryAfter.fold(result) { delay =>
        result.withHeaders("Retry-After" -> math.max(1L, (delay.toMillis + 999L) / 1000L).toString)
      }
    case failure: UseCaseException.Unavailable =>
      render(Results.ServiceUnavailable, pageMeta)(
        views.html.spotify.searchTrack(
          failure.user.displayName,
          failure.query,
          None,
          Some("楽曲を検索できませんでした。時間をおいて再度検索してください。")
        )
      )
  }
}
