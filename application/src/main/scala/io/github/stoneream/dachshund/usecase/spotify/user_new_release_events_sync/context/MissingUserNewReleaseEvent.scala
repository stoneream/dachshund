package io.github.stoneream.dachshund.usecase.spotify.user_new_release_events_sync.context

private[user_new_release_events_sync] final case class MissingUserNewReleaseEvent(
    userId: Long,
    artistReleaseId: Long,
    spotifyReleaseCode: String,
    sourceSpotifyArtistCode: String
)
