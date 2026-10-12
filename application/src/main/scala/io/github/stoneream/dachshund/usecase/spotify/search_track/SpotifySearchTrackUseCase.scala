package io.github.stoneream.dachshund.usecase.spotify.search_track

import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as TokenException
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.search_track.SpotifySearchTrackUseCaseOutput.SearchState
import io.github.stoneream.dachshund.usecase.spotify.search_track.step.SearchSpotifyTracksStep
import io.github.stoneream.dachshund.usecase.spotify.search_track.{SpotifySearchTrackUseCaseException as UseCaseException, SpotifySearchTrackUseCaseInput as UseCaseInput, SpotifySearchTrackUseCaseOutput as UseCaseOutput}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class SpotifySearchTrackUseCase @Inject() (
    searchSpotifyTracksStep: SearchSpotifyTracksStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {
  private given scala.concurrent.ExecutionContext = defaultExecutor

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
    val query = input.query.strip()
    def output(state: SearchState): UseCaseOutput = UseCaseOutput(input.user, query, state)

    if (query.isEmpty) Future.successful(output(SearchState.Initial))
    else
      searchSpotifyTracksStep
        .run(input.user.userId, input.now, query, input.offset)
        .map(page => output(SearchState.Results(page)))
        .recoverWith {
          case _: TokenException.AuthorizationNotFound | _: TokenException.ReauthorizationRequired | _: SpotifyClientException.Unauthorized =>
            Future.failed(UseCaseException.AuthorizationRequired)
          case exception: SpotifyClientException.RateLimited =>
            Future.failed(UseCaseException.RateLimited(input.user, query, exception.retryAfter, exception))
          case NonFatal(exception) =>
            warn("楽曲検索に失敗しました", kv("failureClass", exception.getClass.getName))
            Future.failed(UseCaseException.Unavailable(input.user, query, exception))
        }
  }
}
