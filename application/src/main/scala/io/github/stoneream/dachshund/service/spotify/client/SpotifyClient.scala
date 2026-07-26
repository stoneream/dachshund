package io.github.stoneream.dachshund.service.spotify.client

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyAddItemsToPlaylistResult
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyCreatePlaylistResult
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyFollowedArtistsPage
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyArtistReleasePage
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyPlaylistPage

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

  def getArtistReleasePage(
      accessToken: String,
      spotifyArtistCode: String,
      includeGroups: String,
      market: Option[String],
      limit: Int,
      offset: Int
  )(using loggingContext: LoggingContext): Future[SpotifyArtistReleasePage] =
    Future.failed(
      new UnsupportedOperationException(
        s"getArtistReleasePage is not implemented: " +
          s"accessTokenDefined=${accessToken.nonEmpty}, " +
          s"spotifyArtistCode=$spotifyArtistCode, " +
          s"includeGroups=$includeGroups, " +
          s"market=$market, " +
          s"limit=$limit, " +
          s"offset=$offset, " +
          s"traceId=${loggingContext.traceId}"
      )
    )
}
