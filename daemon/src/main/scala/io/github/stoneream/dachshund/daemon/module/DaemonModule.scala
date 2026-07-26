package io.github.stoneream.dachshund.daemon.module

import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.{Config, ConfigFactory}
import io.github.stoneream.dachshund.config.{ApplicationConfig, ApplicationConfigReader}
import io.github.stoneream.dachshund.daemon.config.{ArtistReleaseSyncQueueJobConfig, ArtistReleasesSyncJobConfig, DaemonConfig, DaemonConfigReader, FollowedArtistsSyncJobConfig, FollowedArtistsSyncQueueJobConfig, SpotifyAccessTokenRefreshJobConfig, UserNewReleaseEventsSyncJobConfig, UserNewReleaseNotificationDeliveryJobConfig}
import io.github.stoneream.dachshund.daemon.job.{JobLoader, JobLoaderImpl, JobRunner, JobRunnerImpl, JobScheduler, JobSchedulerImpl}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor, IoDispatcher}
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.{ArtistReleaseSyncQueueService, ArtistReleaseSyncQueueServiceImpl}
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.{FollowedArtistSyncQueueService, FollowedArtistSyncQueueServiceImpl}
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.{UserNewReleaseNotificationQueueService, UserNewReleaseNotificationQueueServiceImpl}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderImpl}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientImpl}
import io.github.stoneream.dachshund.service.spotify.client_credentials.{SpotifyClientCredentialsAccessTokenProvider, SpotifyClientCredentialsAccessTokenProviderImpl}
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}

class DaemonModule(
    config: Config = ConfigFactory.load(),
    applicationConfig: Option[ApplicationConfig] = None,
    daemonConfig: Option[DaemonConfig] = None
) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[FollowedArtistSyncQueueService]).to(classOf[FollowedArtistSyncQueueServiceImpl])
    bind(classOf[ArtistReleaseSyncQueueService]).to(classOf[ArtistReleaseSyncQueueServiceImpl])
    bind(classOf[UserNewReleaseNotificationQueueService]).to(classOf[UserNewReleaseNotificationQueueServiceImpl])
    bind(classOf[SpotifyOAuthClient]).to(classOf[SpotifyOAuthClientImpl])
    bind(classOf[SpotifyAuthorizationCodeAccessTokenProvider]).to(classOf[SpotifyAuthorizationCodeAccessTokenProviderImpl])
    bind(classOf[SpotifyClientCredentialsAccessTokenProvider]).to(classOf[SpotifyClientCredentialsAccessTokenProviderImpl])
    bind(classOf[SpotifyClient]).to(classOf[SpotifyClientImpl])
    bind(classOf[JobLoader]).to(classOf[JobLoaderImpl])
    bind(classOf[JobRunner]).to(classOf[JobRunnerImpl])
    bind(classOf[JobScheduler]).to(classOf[JobSchedulerImpl])
  }

  @Provides
  @Singleton
  def provideApplicationConfig(): ApplicationConfig =
    applicationConfig.getOrElse(ApplicationConfigReader.load(config))

  @Provides
  @Singleton
  def provideDaemonConfig(): DaemonConfig =
    daemonConfig.getOrElse(DaemonConfigReader.load(config))

  @Provides
  @Singleton
  def provideDefaultExecutor(daemonConfig: DaemonConfig): DefaultExecutor =
    new DefaultExecutorImpl(daemonConfig.executors.defaultExecutor)

  @Provides
  @Singleton
  def provideDatabaseExecutor(daemonConfig: DaemonConfig): DatabaseExecutor =
    new DatabaseExecutorImpl(daemonConfig.executors.databaseExecutor)

  @Provides
  @Singleton
  def provideIoDispatcher(daemonConfig: DaemonConfig): IoDispatcher =
    new IoDispatcherImpl(daemonConfig.executors.ioDispatcher)

  @Provides
  @Singleton
  def provideSpotifyAccessTokenRefreshJobConfig(daemonConfig: DaemonConfig): SpotifyAccessTokenRefreshJobConfig =
    daemonConfig.jobs.spotifyAccessTokenRefresh

  @Provides
  @Singleton
  def provideFollowedArtistsSyncQueueJobConfig(daemonConfig: DaemonConfig): FollowedArtistsSyncQueueJobConfig =
    daemonConfig.jobs.followedArtistsSyncQueue

  @Provides
  @Singleton
  def provideFollowedArtistsSyncJobConfig(daemonConfig: DaemonConfig): FollowedArtistsSyncJobConfig =
    daemonConfig.jobs.followedArtistsSync

  @Provides
  @Singleton
  def provideArtistReleaseSyncQueueJobConfig(daemonConfig: DaemonConfig): ArtistReleaseSyncQueueJobConfig =
    daemonConfig.jobs.artistReleaseSyncQueue

  @Provides
  @Singleton
  def provideArtistReleasesSyncJobConfig(daemonConfig: DaemonConfig): ArtistReleasesSyncJobConfig =
    daemonConfig.jobs.artistReleasesSync

  @Provides
  @Singleton
  def provideUserNewReleaseEventsSyncJobConfig(daemonConfig: DaemonConfig): UserNewReleaseEventsSyncJobConfig =
    daemonConfig.jobs.userNewReleaseEventsSync

  @Provides
  @Singleton
  def provideUserNewReleaseNotificationDeliveryJobConfig(daemonConfig: DaemonConfig): UserNewReleaseNotificationDeliveryJobConfig =
    daemonConfig.jobs.userNewReleaseNotificationDelivery
}
