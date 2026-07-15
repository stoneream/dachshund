package io.github.stoneream.dachshund.service.application.artist_release_sync_queue

enum ArtistReleaseSyncQueueUpdateResult {
  case Updated
  case StaleLockSkipped
}
