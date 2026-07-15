package io.github.stoneream.dachshund.service.application.followed_artists_sync_queue

import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget

enum FollowedArtistSyncQueueProgressResult {
  case Updated(target: FollowedArtistSyncQueueTarget)
  case StaleLockSkipped
}
