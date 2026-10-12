package io.github.stoneream.dachshund.usecase.spotify.track.detail.step

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.reccobeats.{ReccoBeatsClient, ReccoBeatsClientException}
import io.github.stoneream.dachshund.usecase.spotify.track.detail.SpotifyTrackDetailUseCaseOutput.AudioFeaturesState

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
private[detail] class FetchReccoBeatsAudioFeaturesStep @Inject() (
    reccoBeatsClient: ReccoBeatsClient,
    defaultExecutor: DefaultExecutor
) {
  private given scala.concurrent.ExecutionContext = defaultExecutor

  def run(trackId: String)(using LoggingContext): Future[AudioFeaturesState] =
    reccoBeatsClient
      .getTrackAudioFeatures(trackId)
      .map {
        case Some(features) => AudioFeaturesState.Available(features)
        case None => AudioFeaturesState.NotFound
      }
      .recover { case _: ReccoBeatsClientException.AccessDenied => AudioFeaturesState.AccessDenied }
}
