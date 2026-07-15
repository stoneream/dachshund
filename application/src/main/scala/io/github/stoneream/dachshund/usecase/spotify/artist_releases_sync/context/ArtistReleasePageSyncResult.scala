package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context

private[artist_releases_sync] final case class ArtistReleasePageSyncResult(
    releaseCount: Int,
    trackCount: Int
)
