package io.github.stoneream.dachshund.daemon.job

import io.github.stoneream.dachshund.daemon.config.SpotifyAccessTokenRefreshJobConfig
import io.github.stoneream.dachshund.daemon.config.{ArtistReleaseSyncQueueJobConfig, ArtistReleasesSyncJobConfig, FollowedArtistsSyncJobConfig, FollowedArtistsSyncQueueJobConfig, JobName, JobRetryPolicy, JobSchedule, JobSetting, UserNewReleaseEventsSyncJobConfig}
import io.github.stoneream.dachshund.daemon.handler.spotify.{SpotifyAccessTokenRefreshJob, SpotifyAccessTokenRefreshJobHandler}
import io.github.stoneream.dachshund.daemon.handler.spotify.artist_release_sync_queue.{ArtistReleaseSyncQueueHandler, ArtistReleaseSyncQueueJob}
import io.github.stoneream.dachshund.daemon.handler.spotify.artist_releases_sync.{ArtistReleasesSyncHandler, ArtistReleasesSyncJob}
import io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync.{FollowedArtistsSyncHandler, FollowedArtistsSyncJob}
import io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync_queue.{FollowedArtistsSyncQueueHandler, FollowedArtistsSyncQueueJob}
import io.github.stoneream.dachshund.daemon.handler.spotify.user_new_release_events_sync.{UserNewReleaseEventsSyncHandler, UserNewReleaseEventsSyncJob}
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec
import zio.{Exit, Runtime, Task, Unsafe}

import scala.concurrent.duration.*

class JobLoaderImplSpec extends AnyFeatureSpec with IdiomaticMockito {
  Feature("job loader") {
    Scenario("登録された全 job を返す") {
      val spotifyJob = new SpotifyAccessTokenRefreshJob(mock[SpotifyAccessTokenRefreshJobHandler], spotifyAccessTokenRefreshJobConfig)
      val followedJob = new FollowedArtistsSyncQueueJob(mock[FollowedArtistsSyncQueueHandler], followedArtistsSyncQueueJobConfig)
      val followedSyncJob = new FollowedArtistsSyncJob(mock[FollowedArtistsSyncHandler], followedArtistsSyncJobConfig)
      val artistReleaseJob = new ArtistReleaseSyncQueueJob(mock[ArtistReleaseSyncQueueHandler], artistReleaseSyncQueueJobConfig)
      val artistReleasesSyncJob = new ArtistReleasesSyncJob(mock[ArtistReleasesSyncHandler], artistReleasesSyncJobConfig)
      val userNewReleaseEventsSyncJob =
        new UserNewReleaseEventsSyncJob(mock[UserNewReleaseEventsSyncHandler], userNewReleaseEventsSyncJobConfig)
      val loader = new JobLoaderImpl(
        spotifyJob,
        followedJob,
        followedSyncJob,
        artistReleaseJob,
        artistReleasesSyncJob,
        userNewReleaseEventsSyncJob
      )

      val jobs = unsafeRun(loader.load())

      assert(
        jobs.map(_.setting.name) == List(
          JobName("spotify-access-token-refresh"),
          JobName("followed-artists-sync-queue"),
          JobName("followed-artists-sync"),
          JobName("artist-release-sync-queue"),
          JobName("artist-releases-sync"),
          JobName("user-new-release-events-sync")
        )
      )
    }
  }

  private def spotifyAccessTokenRefreshJobConfig: SpotifyAccessTokenRefreshJobConfig =
    SpotifyAccessTokenRefreshJobConfig(
      setting = JobSetting(
        name = JobName("spotify-access-token-refresh"),
        schedule = JobSchedule.Every(1.minute),
        timeout = 5.minutes,
        retryPolicy = jobRetryPolicy
      ),
      batchSize = 50
    )

  private def followedArtistsSyncQueueJobConfig: FollowedArtistsSyncQueueJobConfig =
    FollowedArtistsSyncQueueJobConfig(
      setting = JobSetting(
        name = JobName("followed-artists-sync-queue"),
        schedule = JobSchedule.Every(1.hour),
        timeout = 5.minutes,
        retryPolicy = jobRetryPolicy
      )
    )

  private def followedArtistsSyncJobConfig: FollowedArtistsSyncJobConfig =
    FollowedArtistsSyncJobConfig(
      setting = JobSetting(
        name = JobName("followed-artists-sync"),
        schedule = JobSchedule.Every(1.minute),
        timeout = 5.minutes,
        retryPolicy = jobRetryPolicy
      ),
      batchSize = 25,
      processingLease = 1.hour
    )

  private def artistReleaseSyncQueueJobConfig: ArtistReleaseSyncQueueJobConfig =
    ArtistReleaseSyncQueueJobConfig(
      setting = JobSetting(
        name = JobName("artist-release-sync-queue"),
        schedule = JobSchedule.Every(1.hour),
        timeout = 5.minutes,
        retryPolicy = jobRetryPolicy
      )
    )

  private def artistReleasesSyncJobConfig: ArtistReleasesSyncJobConfig =
    ArtistReleasesSyncJobConfig(
      setting = JobSetting(
        name = JobName("artist-releases-sync"),
        schedule = JobSchedule.Every(1.minute),
        timeout = 10.minutes,
        retryPolicy = jobRetryPolicy
      ),
      batchSize = 5,
      processingLease = 1.hour
    )

  private def userNewReleaseEventsSyncJobConfig: UserNewReleaseEventsSyncJobConfig =
    UserNewReleaseEventsSyncJobConfig(
      setting = JobSetting(
        name = JobName("user-new-release-events-sync"),
        schedule = JobSchedule.Every(1.minute),
        timeout = 5.minutes,
        retryPolicy = jobRetryPolicy
      ),
      batchSize = 500
    )

  private def jobRetryPolicy: JobRetryPolicy =
    JobRetryPolicy(
      maxAttempts = 1,
      baseDelay = 0.seconds,
      maxDelay = 0.seconds,
      jitterRatio = None
    )

  private def unsafeRun[A](task: Task[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task) match {
        case Exit.Success(value) => value
        case Exit.Failure(cause) => throw cause.squash
      }
    }
}
