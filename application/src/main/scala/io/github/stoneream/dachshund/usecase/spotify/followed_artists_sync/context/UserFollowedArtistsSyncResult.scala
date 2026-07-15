package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context

private[followed_artists_sync] final case class UserFollowedArtistsSyncResult(
    upsertedCount: Int,
    deletedCount: Int
)
