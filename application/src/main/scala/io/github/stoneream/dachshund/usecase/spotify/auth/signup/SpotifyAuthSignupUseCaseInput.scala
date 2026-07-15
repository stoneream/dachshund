package io.github.stoneream.dachshund.usecase.spotify.auth.signup

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class SpotifyAuthSignupUseCaseInput(
    now: BusinessDateTime
)
