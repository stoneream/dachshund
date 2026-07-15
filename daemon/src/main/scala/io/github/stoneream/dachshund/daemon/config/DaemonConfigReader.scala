package io.github.stoneream.dachshund.daemon.config

import com.typesafe.config.Config
import io.github.stoneream.dachshund.logging.Logger
import pureconfig.ConfigSource

object DaemonConfigReader extends Logger {
  def load(config: Config): DaemonConfig = {
    val daemonConfig = ConfigSource.fromConfig(config).at("daemon").loadOrThrow[DaemonConfig]
    logging(daemonConfig)
    daemonConfig
  }

  private def logging(daemonConfig: DaemonConfig): Unit = {
    val executors = daemonConfig.executors
    val spotifyAccessTokenRefresh = daemonConfig.jobs.spotifyAccessTokenRefresh
    val followedArtistsSyncQueue = daemonConfig.jobs.followedArtistsSyncQueue
    val followedArtistsSync = daemonConfig.jobs.followedArtistsSync
    val artistReleaseSyncQueue = daemonConfig.jobs.artistReleaseSyncQueue
    val artistReleasesSync = daemonConfig.jobs.artistReleasesSync
    val userNewReleaseEventsSync = daemonConfig.jobs.userNewReleaseEventsSync
    logger.info(
      "daemon 設定を読み込みました",
      kv("daemon.executors.defaultExecutor.threadCount", executors.defaultExecutor.threadCount),
      kv("daemon.executors.defaultExecutor.shutdownGracePeriod", executors.defaultExecutor.shutdownGracePeriod.toString),
      kv("daemon.executors.databaseExecutor.threadCount", executors.databaseExecutor.threadCount),
      kv("daemon.executors.databaseExecutor.shutdownGracePeriod", executors.databaseExecutor.shutdownGracePeriod.toString),
      kv("daemon.executors.ioDispatcher.threadCount", executors.ioDispatcher.threadCount),
      kv("daemon.executors.ioDispatcher.shutdownGracePeriod", executors.ioDispatcher.shutdownGracePeriod.toString),
      kv("daemon.jobs.spotifyAccessTokenRefresh.schedule", spotifyAccessTokenRefresh.setting.schedule.toString),
      kv("daemon.jobs.spotifyAccessTokenRefresh.timeout", spotifyAccessTokenRefresh.setting.timeout.toString),
      kv("daemon.jobs.spotifyAccessTokenRefresh.retry.maxAttempts", spotifyAccessTokenRefresh.setting.retryPolicy.maxAttempts),
      kv("daemon.jobs.spotifyAccessTokenRefresh.retry.baseDelay", spotifyAccessTokenRefresh.setting.retryPolicy.baseDelay.toString),
      kv("daemon.jobs.spotifyAccessTokenRefresh.retry.maxDelay", spotifyAccessTokenRefresh.setting.retryPolicy.maxDelay.toString),
      kv("daemon.jobs.spotifyAccessTokenRefresh.batchSize", spotifyAccessTokenRefresh.batchSize),
      kv("daemon.jobs.followedArtistsSyncQueue.schedule", followedArtistsSyncQueue.setting.schedule.toString),
      kv("daemon.jobs.followedArtistsSyncQueue.timeout", followedArtistsSyncQueue.setting.timeout.toString),
      kv("daemon.jobs.followedArtistsSyncQueue.retry.maxAttempts", followedArtistsSyncQueue.setting.retryPolicy.maxAttempts),
      kv("daemon.jobs.followedArtistsSyncQueue.retry.baseDelay", followedArtistsSyncQueue.setting.retryPolicy.baseDelay.toString),
      kv("daemon.jobs.followedArtistsSyncQueue.retry.maxDelay", followedArtistsSyncQueue.setting.retryPolicy.maxDelay.toString),
      kv("daemon.jobs.followedArtistsSync.schedule", followedArtistsSync.setting.schedule.toString),
      kv("daemon.jobs.followedArtistsSync.timeout", followedArtistsSync.setting.timeout.toString),
      kv("daemon.jobs.followedArtistsSync.retry.maxAttempts", followedArtistsSync.setting.retryPolicy.maxAttempts),
      kv("daemon.jobs.followedArtistsSync.retry.baseDelay", followedArtistsSync.setting.retryPolicy.baseDelay.toString),
      kv("daemon.jobs.followedArtistsSync.retry.maxDelay", followedArtistsSync.setting.retryPolicy.maxDelay.toString),
      kv("daemon.jobs.followedArtistsSync.batchSize", followedArtistsSync.batchSize),
      kv("daemon.jobs.followedArtistsSync.processingLease", followedArtistsSync.processingLease.toString),
      kv("daemon.jobs.artistReleaseSyncQueue.schedule", artistReleaseSyncQueue.setting.schedule.toString),
      kv("daemon.jobs.artistReleaseSyncQueue.timeout", artistReleaseSyncQueue.setting.timeout.toString),
      kv("daemon.jobs.artistReleaseSyncQueue.retry.maxAttempts", artistReleaseSyncQueue.setting.retryPolicy.maxAttempts),
      kv("daemon.jobs.artistReleaseSyncQueue.retry.baseDelay", artistReleaseSyncQueue.setting.retryPolicy.baseDelay.toString),
      kv("daemon.jobs.artistReleaseSyncQueue.retry.maxDelay", artistReleaseSyncQueue.setting.retryPolicy.maxDelay.toString),
      kv("daemon.jobs.artistReleasesSync.schedule", artistReleasesSync.setting.schedule.toString),
      kv("daemon.jobs.artistReleasesSync.timeout", artistReleasesSync.setting.timeout.toString),
      kv("daemon.jobs.artistReleasesSync.retry.maxAttempts", artistReleasesSync.setting.retryPolicy.maxAttempts),
      kv("daemon.jobs.artistReleasesSync.retry.baseDelay", artistReleasesSync.setting.retryPolicy.baseDelay.toString),
      kv("daemon.jobs.artistReleasesSync.retry.maxDelay", artistReleasesSync.setting.retryPolicy.maxDelay.toString),
      kv("daemon.jobs.artistReleasesSync.batchSize", artistReleasesSync.batchSize),
      kv("daemon.jobs.artistReleasesSync.processingLease", artistReleasesSync.processingLease.toString),
      kv("daemon.jobs.userNewReleaseEventsSync.schedule", userNewReleaseEventsSync.setting.schedule.toString),
      kv("daemon.jobs.userNewReleaseEventsSync.timeout", userNewReleaseEventsSync.setting.timeout.toString),
      kv("daemon.jobs.userNewReleaseEventsSync.retry.maxAttempts", userNewReleaseEventsSync.setting.retryPolicy.maxAttempts),
      kv("daemon.jobs.userNewReleaseEventsSync.retry.baseDelay", userNewReleaseEventsSync.setting.retryPolicy.baseDelay.toString),
      kv("daemon.jobs.userNewReleaseEventsSync.retry.maxDelay", userNewReleaseEventsSync.setting.retryPolicy.maxDelay.toString),
      kv("daemon.jobs.userNewReleaseEventsSync.batchSize", userNewReleaseEventsSync.batchSize)
    )
  }
}
