package io.github.stoneream.dachshund.daemon.job

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.daemon.handler.spotify.SpotifyAccessTokenRefreshJob
import io.github.stoneream.dachshund.daemon.handler.spotify.artist_release_sync_queue.ArtistReleaseSyncQueueJob
import io.github.stoneream.dachshund.daemon.handler.spotify.artist_releases_sync.ArtistReleasesSyncJob
import io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync.FollowedArtistsSyncJob
import io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync_queue.FollowedArtistsSyncQueueJob
import io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_events_sync.UserNewReleaseEventsSyncJob
import io.github.stoneream.dachshund.daemon.job.model.Job
import zio.{Task, ZIO}

@Singleton
class JobLoaderImpl @Inject() (
    spotifyAccessTokenRefreshJob: SpotifyAccessTokenRefreshJob,
    followedArtistsSyncQueueJob: FollowedArtistsSyncQueueJob,
    followedArtistsSyncJob: FollowedArtistsSyncJob,
    artistReleaseSyncQueueJob: ArtistReleaseSyncQueueJob,
    artistReleasesSyncJob: ArtistReleasesSyncJob,
    userNewReleaseEventsSyncJob: UserNewReleaseEventsSyncJob
) extends JobLoader {
  override def load(): Task[List[Job]] =
    ZIO.succeed(
      List(
        spotifyAccessTokenRefreshJob,
        followedArtistsSyncQueueJob,
        followedArtistsSyncJob,
        artistReleaseSyncQueueJob,
        artistReleasesSyncJob,
        userNewReleaseEventsSyncJob
      )
    )
}
