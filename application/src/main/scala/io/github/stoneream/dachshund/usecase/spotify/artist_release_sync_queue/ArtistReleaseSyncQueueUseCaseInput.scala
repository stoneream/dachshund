package io.github.stoneream.dachshund.usecase.spotify.artist_release_sync_queue

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class ArtistReleaseSyncQueueUseCaseInput(
    now: BusinessDateTime
)
