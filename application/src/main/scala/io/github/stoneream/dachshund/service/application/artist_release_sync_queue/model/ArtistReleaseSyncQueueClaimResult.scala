package io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model

final case class ArtistReleaseSyncQueueClaimResult(
    target: ArtistReleaseSyncQueueTarget,
    claimed: Boolean
)
