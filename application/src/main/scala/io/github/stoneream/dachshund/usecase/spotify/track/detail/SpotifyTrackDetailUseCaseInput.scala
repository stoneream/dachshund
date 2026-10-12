package io.github.stoneream.dachshund.usecase.spotify.track.detail

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class SpotifyTrackDetailUseCaseInput(
    user: UserSessionContext.NormalUser,
    now: BusinessDateTime,
    trackId: String
)
