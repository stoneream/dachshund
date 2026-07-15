package io.github.stoneream.dachshund.service.application.followed_artists_sync_queue

enum FollowedArtistSyncQueueUpdateResult {
  case Updated
  case StaleLockSkipped
}
