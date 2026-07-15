package io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model

final case class FollowedArtistSyncQueueClaimResult(
    target: FollowedArtistSyncQueueTarget,
    claimed: Boolean
)
