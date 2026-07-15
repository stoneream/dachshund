package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync_queue

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class FollowedArtistsSyncQueueUseCaseInput(
    now: BusinessDateTime
)
