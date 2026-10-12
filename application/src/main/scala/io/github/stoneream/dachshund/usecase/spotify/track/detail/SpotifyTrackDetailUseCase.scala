package io.github.stoneream.dachshund.usecase.spotify.track.detail

import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as TokenException
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException
import io.github.stoneream.dachshund.service.reccobeats.ReccoBeatsClientException
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.spotify.track.detail.step.{FetchReccoBeatsAudioFeaturesStep, FetchSpotifyTrackStep}
import io.github.stoneream.dachshund.usecase.spotify.track.detail.{SpotifyTrackDetailUseCaseException as UseCaseException, SpotifyTrackDetailUseCaseInput as UseCaseInput, SpotifyTrackDetailUseCaseOutput as UseCaseOutput}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class SpotifyTrackDetailUseCase @Inject() (
    fetchSpotifyTrackStep: FetchSpotifyTrackStep,
    fetchReccoBeatsAudioFeaturesStep: FetchReccoBeatsAudioFeaturesStep,
    defaultExecutor: DefaultExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {

  private given scala.concurrent.ExecutionContext = defaultExecutor

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] =
    (for {
      track <- fetchSpotifyTrackStep.run(input)
      audioFeatures <- fetchReccoBeatsAudioFeaturesStep.run(track.spotifyTrackCode)
    } yield UseCaseOutput(input.user, track, audioFeatures)).recoverWith {
      case exception: UseCaseException => Future.failed(exception)
      case _: TokenException.AuthorizationNotFound | _: TokenException.ReauthorizationRequired | _: SpotifyClientException.Unauthorized =>
        Future.failed(UseCaseException.AuthorizationRequired)
      case _: SpotifyClientException.Forbidden => Future.failed(UseCaseException.Forbidden(input.user))
      case exception: SpotifyClientException.RateLimited =>
        Future.failed(UseCaseException.RateLimited(input.user, exception.retryAfter, exception))
      case exception: ReccoBeatsClientException.RateLimited =>
        Future.failed(UseCaseException.RateLimited(input.user, exception.retryAfter, exception))
      case NonFatal(exception) =>
        warn("楽曲情報の取得に失敗しました", kv("failureClass", exception.getClass.getName))
        Future.failed(UseCaseException.Unavailable(input.user, exception))
    }
}
