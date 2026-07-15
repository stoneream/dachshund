package io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserNewReleaseEventsSyncUseCaseInput(
    now: BusinessDateTime,
    batchSize: Int
)
