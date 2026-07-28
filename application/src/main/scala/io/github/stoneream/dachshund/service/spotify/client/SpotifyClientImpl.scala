package io.github.stoneream.dachshund.service.spotify.client

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.SpotifyArtistReleasesApi
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyArtistReleaseSummaryPage}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.SpotifyFollowedArtistsApi
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model.SpotifyFollowedArtistsPage
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.SpotifyPlaylistApi
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.model.{SpotifyAddItemsToPlaylistResult, SpotifyCreatePlaylistResult, SpotifyPlaylistPage}

import scala.concurrent.Future

@Singleton
class SpotifyClientImpl @Inject() (
    followedArtistsApi: SpotifyFollowedArtistsApi,
    artistReleasesApi: SpotifyArtistReleasesApi,
    playlistApi: SpotifyPlaylistApi
) extends SpotifyClient {
  override def getFollowedArtists(
      accessToken: String,
      afterCursor: Option[String],
      limit: Int
  )(using LoggingContext): Future[SpotifyFollowedArtistsPage] =
    followedArtistsApi.getFollowedArtists(accessToken, afterCursor, limit)

  override def getArtistReleaseSummaryPage(
      accessToken: String,
      spotifyArtistCode: String,
      includeGroups: String,
      market: Option[String],
      limit: Int,
      offset: Int
  )(using LoggingContext): Future[SpotifyArtistReleaseSummaryPage] =
    artistReleasesApi.getArtistReleaseSummaryPage(
      accessToken = accessToken,
      spotifyArtistCode = spotifyArtistCode,
      includeGroups = includeGroups,
      market = market,
      limit = limit,
      offset = offset
    )

  override def getArtistRelease(
      accessToken: String,
      sourceSpotifyArtistCode: String,
      summary: SpotifyArtistReleaseSummary,
      market: Option[String]
  )(using LoggingContext): Future[SpotifyArtistRelease] =
    artistReleasesApi.getArtistRelease(
      accessToken = accessToken,
      sourceSpotifyArtistCode = sourceSpotifyArtistCode,
      summary = summary,
      market = market
    )

  override def addItemsToPlaylist(
      accessToken: String,
      spotifyPlaylistCode: String,
      trackUris: Seq[String]
  )(using LoggingContext): Future[SpotifyAddItemsToPlaylistResult] =
    playlistApi.addItemsToPlaylist(accessToken, spotifyPlaylistCode, trackUris)

  override def getCurrentUserPlaylistPage(
      accessToken: String,
      limit: Int,
      offset: Int
  )(using LoggingContext): Future[SpotifyPlaylistPage] =
    playlistApi.getCurrentUserPlaylistPage(accessToken, limit, offset)

  override def createCurrentUserPlaylist(
      accessToken: String,
      playlistName: String,
      isPublic: Boolean
  )(using LoggingContext): Future[SpotifyCreatePlaylistResult] =
    playlistApi.createCurrentUserPlaylist(accessToken, playlistName, isPublic)

  override def unfollowPlaylist(
      accessToken: String,
      spotifyPlaylistCode: String
  )(using LoggingContext): Future[Unit] =
    playlistApi.unfollowPlaylist(accessToken, spotifyPlaylistCode)
}
