package io.github.stoneream.dachshund.usecase.user_settings.apply.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClient

import scala.concurrent.Future

@Singleton
private[apply] class CleanupSpotifyManagedPlaylistStep @Inject() (
    spotifyClient: SpotifyClient
) {
  def run(
      accessToken: String,
      spotifyPlaylistCode: String
  )(using LoggingContext): Future[Unit] =
    spotifyClient.unfollowPlaylist(
      accessToken = accessToken,
      spotifyPlaylistCode = spotifyPlaylistCode
    )
}
