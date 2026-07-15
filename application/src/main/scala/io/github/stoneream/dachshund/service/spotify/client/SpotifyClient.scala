package io.github.stoneream.dachshund.service.spotify.client

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyFollowedArtistsPage
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyArtistReleasePage

import scala.concurrent.Future

trait SpotifyClient {
  def getFollowedArtists(
      accessToken: String,
      afterCursor: Option[String],
      limit: Int
  )(using LoggingContext): Future[SpotifyFollowedArtistsPage]

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
