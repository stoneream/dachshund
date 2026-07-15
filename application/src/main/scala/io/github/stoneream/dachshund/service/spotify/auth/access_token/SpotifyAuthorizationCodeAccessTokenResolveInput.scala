package io.github.stoneream.dachshund.service.spotify.auth.access_token

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class SpotifyAuthorizationCodeAccessTokenResolveInput(
    userId: Long,
    now: BusinessDateTime,
    forceRefresh: Boolean = false
)
