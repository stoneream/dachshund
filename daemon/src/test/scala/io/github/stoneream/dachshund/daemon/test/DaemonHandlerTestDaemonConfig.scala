package io.github.stoneream.dachshund.daemon.test

import io.github.stoneream.dachshund.daemon.config.{ArtistReleaseSyncQueueJobConfig, ArtistReleasesSyncJobConfig, DaemonConfig, DaemonExecutorConfig, DaemonExecutorsConfig, DaemonJobsConfig, FollowedArtistsSyncJobConfig, FollowedArtistsSyncQueueJobConfig, JobName, JobRetryPolicy, JobSchedule, JobSetting, SpotifyAccessTokenRefreshJobConfig, UserNewReleaseEventsSyncJobConfig, UserNewReleaseNotificationDeliveryJobConfig}

import scala.concurrent.duration.*

private[test] object DaemonHandlerTestDaemonConfig {
  val default: DaemonConfig =
    DaemonConfig(
      executors = DaemonExecutorsConfig(
        defaultExecutor = DaemonExecutorConfig(
          threadCount = 1,
          shutdownGracePeriod = 1.second
        ),
        databaseExecutor = DaemonExecutorConfig(
          threadCount = 1,
          shutdownGracePeriod = 1.second
        ),
        ioDispatcher = DaemonExecutorConfig(
          threadCount = 1,
          shutdownGracePeriod = 1.second
        )
      ),
      jobs = DaemonJobsConfig(
        spotifyAccessTokenRefresh = SpotifyAccessTokenRefreshJobConfig(
          setting = jobSetting("spotify-access-token-refresh"),
          batchSize = 1
        ),
        followedArtistsSyncQueue = FollowedArtistsSyncQueueJobConfig(
          setting = jobSetting("followed-artists-sync-queue")
        ),
        followedArtistsSync = FollowedArtistsSyncJobConfig(
          setting = jobSetting("followed-artists-sync"),
          batchSize = 1,
          processingLease = 1.minute
        ),
        artistReleaseSyncQueue = ArtistReleaseSyncQueueJobConfig(
          setting = jobSetting("artist-release-sync-queue")
        ),
        artistReleasesSync = ArtistReleasesSyncJobConfig(
          setting = jobSetting("artist-releases-sync"),
          batchSize = 1,
          processingLease = 1.minute
        ),
        userNewReleaseEventsSync = UserNewReleaseEventsSyncJobConfig(
          setting = jobSetting("user-new-release-events-sync"),
          batchSize = 1
        ),
        userNewReleaseNotificationDelivery = UserNewReleaseNotificationDeliveryJobConfig(
          setting = jobSetting("user-new-release-notification-delivery"),
          batchSize = 1,
          processingLease = 1.minute
        )
      )
    )

  private def jobSetting(name: String): JobSetting =
    JobSetting(
      name = JobName(name),
      enabled = true,
      schedule = JobSchedule.Every(1.minute),
      timeout = 1.minute,
      retryPolicy = JobRetryPolicy(
        maxAttempts = 1,
        baseDelay = 1.second,
        maxDelay = 1.second,
        jitterRatio = None
      )
    )
}
