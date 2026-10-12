package io.github.stoneream.dachshund.usecase.spotify.track.detail.step

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenResolveInput}
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_track.model.SpotifyTrackDetail
import io.github.stoneream.dachshund.usecase.spotify.track.detail.{SpotifyTrackDetailUseCaseException as UseCaseException, SpotifyTrackDetailUseCaseInput as UseCaseInput}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
private[detail] class FetchSpotifyTrackStep @Inject() (
    accessTokenProvider: SpotifyAuthorizationCodeAccessTokenProvider,
    spotifyClient: SpotifyClient,
    defaultExecutor: DefaultExecutor
) {
  private given scala.concurrent.ExecutionContext = defaultExecutor

  def run(input: UseCaseInput)(using LoggingContext): Future[SpotifyTrackDetail] =
    fetchTrack(input, forceRefresh = false)

  private def fetchTrack(input: UseCaseInput, forceRefresh: Boolean)(using LoggingContext): Future[SpotifyTrackDetail] =
    accessTokenProvider
      .resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(input.user.userId, input.now, forceRefresh))
      .flatMap { token =>
        spotifyClient
          .getTrack(token.accessToken, input.trackId)
          .flatMap {
            case None => Future.failed(UseCaseException.TrackNotFound(input.user))
            case Some(track) => Future.successful(track)
          }
          .recoverWith {
            case _: SpotifyClientException.Unauthorized if !forceRefresh => fetchTrack(input, forceRefresh = true)
          }
      }
}
