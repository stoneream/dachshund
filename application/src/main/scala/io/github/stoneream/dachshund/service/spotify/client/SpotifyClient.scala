package io.github.stoneream.dachshund.service.spotify.client

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyArtistReleaseSummaryPage}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model.SpotifyFollowedArtistsPage
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.model.{SpotifyAddItemsToPlaylistResult, SpotifyCreatePlaylistResult, SpotifyPlaylistPage}

import scala.concurrent.Future

trait SpotifyClient {
  def getFollowedArtists(
      accessToken: String,
      afterCursor: Option[String],
      limit: Int
  )(using LoggingContext): Future[SpotifyFollowedArtistsPage]

  def addItemsToPlaylist(
      accessToken: String,
      spotifyPlaylistCode: String,
      trackUris: Seq[String]
  )(using LoggingContext): Future[SpotifyAddItemsToPlaylistResult]

  def getCurrentUserPlaylistPage(
      accessToken: String,
      limit: Int,
      offset: Int
  )(using LoggingContext): Future[SpotifyPlaylistPage]

  def createCurrentUserPlaylist(
      accessToken: String,
      playlistName: String,
      isPublic: Boolean
  )(using LoggingContext): Future[SpotifyCreatePlaylistResult]

  def unfollowPlaylist(
      accessToken: String,
      spotifyPlaylistCode: String
  )(using LoggingContext): Future[Unit]

  def getArtistReleaseSummaryPage(
      accessToken: String,
      spotifyArtistCode: String,
      includeGroups: String,
      market: Option[String],
      limit: Int,
      offset: Int
  )(using loggingContext: LoggingContext): Future[SpotifyArtistReleaseSummaryPage]

  def getArtistRelease(
      accessToken: String,
      sourceSpotifyArtistCode: String,
      summary: SpotifyArtistReleaseSummary,
      market: Option[String]
  )(using loggingContext: LoggingContext): Future[SpotifyArtistRelease]
}
