package io.github.stoneream.dachshund.usecase.spotify.auth.refresh

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class SpotifyAccessTokenRefreshUseCaseInput(
    now: BusinessDateTime,
    batchSize: Int
) {
  require(batchSize > 0, "batchSize は正の値である必要があります")
}
