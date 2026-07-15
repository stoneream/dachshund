package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

import scala.concurrent.duration.FiniteDuration

final case class ArtistReleasesSyncUseCaseInput(
    now: BusinessDateTime,
    batchSize: Int,
    processingLease: FiniteDuration
)
