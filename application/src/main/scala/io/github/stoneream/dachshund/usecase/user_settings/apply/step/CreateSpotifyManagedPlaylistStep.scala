package io.github.stoneream.dachshund.usecase.user_settings.apply.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClient
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyCreatePlaylistResult

import scala.concurrent.Future

@Singleton
private[apply] class CreateSpotifyManagedPlaylistStep @Inject() (
    spotifyClient: SpotifyClient
) {
  def run(
      accessToken: String,
      playlistName: String
  )(using LoggingContext): Future[SpotifyCreatePlaylistResult] =
    spotifyClient.createCurrentUserPlaylist(
      accessToken = accessToken,
      playlistName = playlistName,
      isPublic = false
    )
}
