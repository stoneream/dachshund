package io.github.stoneream.dachshund.daemon.config

import pureconfig.ConfigReader

final case class DaemonJobsConfig(
    spotifyAccessTokenRefresh: SpotifyAccessTokenRefreshJobConfig,
    followedArtistsSyncQueue: FollowedArtistsSyncQueueJobConfig,
    followedArtistsSync: FollowedArtistsSyncJobConfig,
    artistReleaseSyncQueue: ArtistReleaseSyncQueueJobConfig,
    artistReleasesSync: ArtistReleasesSyncJobConfig,
    userNewReleaseEventsSync: UserNewReleaseEventsSyncJobConfig,
    userNewReleaseNotificationDelivery: UserNewReleaseNotificationDeliveryJobConfig
) derives ConfigReader
