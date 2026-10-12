package io.github.stoneream.dachshund.usecase.spotify.search_track

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class SpotifySearchTrackUseCaseInput(
    user: UserSessionContext.NormalUser,
    now: BusinessDateTime,
    query: String,
    offset: Int
)
