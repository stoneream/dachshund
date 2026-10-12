package io.github.stoneream.dachshund.usecase.spotify.search_track.step

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model.SpotifyTrackSearchPage

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
private[search_track] class SearchSpotifyTracksStep @Inject() (
    accessTokenProvider: SpotifyAuthorizationCodeAccessTokenProvider,
    spotifyClient: SpotifyClient,
    defaultExecutor: DefaultExecutor
) {
  private given scala.concurrent.ExecutionContext = defaultExecutor

  def run(userId: Long, now: BusinessDateTime, query: String, offset: Int)(using LoggingContext): Future[SpotifyTrackSearchPage] =
    search(userId, now, query, offset, forceRefresh = false)

  private def search(
      userId: Long,
      now: BusinessDateTime,
      query: String,
      offset: Int,
      forceRefresh: Boolean
  )(using LoggingContext): Future[SpotifyTrackSearchPage] =
    accessTokenProvider
      .resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId, now, forceRefresh))
      .flatMap { token =>
        spotifyClient
          .searchTracks(token.accessToken, query, SpotifyTrackSearchPage.PageSize, offset)
          .recoverWith {
            case _: SpotifyClientException.Unauthorized if !forceRefresh =>
              search(userId, now, query, offset, forceRefresh = true)
          }
      }
}
