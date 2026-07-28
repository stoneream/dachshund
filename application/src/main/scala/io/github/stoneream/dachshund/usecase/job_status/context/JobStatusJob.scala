package io.github.stoneream.dachshund.usecase.job_status.context

enum JobStatusJob(val name: String, val title: String) {
  case SpotifyAccessTokenRefresh extends JobStatusJob("spotify-access-token-refresh", "Spotify access token refresh")
  case FollowedArtistsSync extends JobStatusJob("followed-artists-sync", "Followed artists sync")
  case ArtistReleasesSync extends JobStatusJob("artist-releases-sync", "Artist releases sync")
  case UserNewReleaseEventsSync extends JobStatusJob("user-new-release-events-sync", "User new release events sync")
  case UserNewReleaseNotificationDeliveryQueue
      extends JobStatusJob("user-new-release-notification-delivery-queue", "User new release notification delivery queue")
}

object JobStatusJob {
  val All: Seq[JobStatusJob] = Seq(
    SpotifyAccessTokenRefresh,
    FollowedArtistsSync,
    ArtistReleasesSync,
    UserNewReleaseEventsSync,
    UserNewReleaseNotificationDeliveryQueue
  )

  def fromName(name: String): Option[JobStatusJob] =
    All.find(_.name == name)
}
