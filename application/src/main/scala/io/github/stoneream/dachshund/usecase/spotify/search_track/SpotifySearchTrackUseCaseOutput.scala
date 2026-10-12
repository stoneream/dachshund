package io.github.stoneream.dachshund.usecase.spotify.search_track

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model.SpotifyTrackSearchPage

final case class SpotifySearchTrackUseCaseOutput(
    user: UserSessionContext.NormalUser,
    query: String,
    state: SpotifySearchTrackUseCaseOutput.SearchState
)

object SpotifySearchTrackUseCaseOutput {
  enum SearchState {
    case Initial
    case Results(page: SpotifyTrackSearchPage)
  }
}
