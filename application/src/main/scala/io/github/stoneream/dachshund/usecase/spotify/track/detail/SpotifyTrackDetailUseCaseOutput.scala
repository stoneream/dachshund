package io.github.stoneream.dachshund.usecase.spotify.track.detail

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_track.model.SpotifyTrackDetail
import io.github.stoneream.dachshund.service.reccobeats.model.ReccoBeatsAudioFeatures

final case class SpotifyTrackDetailUseCaseOutput(
    user: UserSessionContext.NormalUser,
    track: SpotifyTrackDetail,
    audioFeatures: SpotifyTrackDetailUseCaseOutput.AudioFeaturesState
)

object SpotifyTrackDetailUseCaseOutput {
  enum AudioFeaturesState {
    case Available(features: ReccoBeatsAudioFeatures)
    case AccessDenied
    case NotFound
  }
}
