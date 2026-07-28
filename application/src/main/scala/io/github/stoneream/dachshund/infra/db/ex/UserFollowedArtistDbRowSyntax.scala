package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserFollowedArtistDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model.SpotifyFollowedArtist

object UserFollowedArtistDbRowSyntax {

  extension (source: UserFollowedArtistSource) {
    def toUserFollowedArtistDbRow: UserFollowedArtistDbRow = {
      import DbRowValues.*

      UserFollowedArtistDbRow(
        id = source.id,
        userId = source.userId,
        spotifyArtistCode = source.spotifyArtistCode,
        artistName = source.artistName,
        spotifyArtistUri = source.spotifyArtistUri,
        spotifyUrl = source.spotifyUrl,
        href = source.href,
        primaryImageUrl = source.primaryImageUrl,
        primaryImageHeight = source.primaryImageHeight,
        primaryImageWidth = source.primaryImageWidth,
        imagesJson = source.imagesJson,
        genresJson = source.genresJson,
        followersTotal = source.followersTotal,
        popularity = source.popularity,
        firstFollowedAt = source.firstFollowedAt.dbDateTime,
        lastSeenAt = source.lastSeenAt.dbDateTime,
        lastSyncedAt = source.lastSyncedAt.dbDateTime,
        createdAt = source.createdAt.dbDateTime,
        updatedAt = source.updatedAt.dbDateTime,
        deletedAt = source.deletedAt.dbDateTime,
        createdUser = source.createdUser.dbAuditUser,
        updatedUser = source.updatedUser.dbAuditUser,
        deletedUser = source.deletedUser.dbAuditUser,
        deleted = source.deleted,
        lockVersion = source.lockVersion
      )
    }
  }

  extension (artist: SpotifyFollowedArtist) {
    def toUserFollowedArtistDbRow(
        userId: Long,
        firstFollowedAt: BusinessDateTime,
        lastSeenAt: BusinessDateTime,
        lastSyncedAt: BusinessDateTime
    ): UserFollowedArtistDbRow =
      UserFollowedArtistSource(
        userId = userId,
        spotifyArtistCode = artist.spotifyArtistCode,
        artistName = artist.artistName,
        spotifyArtistUri = artist.spotifyArtistUri,
        spotifyUrl = artist.spotifyUrl,
        href = artist.href,
        primaryImageUrl = artist.primaryImageUrl,
        primaryImageHeight = artist.primaryImageHeight,
        primaryImageWidth = artist.primaryImageWidth,
        imagesJson = artist.imagesJson,
        genresJson = artist.genresJson,
        followersTotal = artist.followersTotal,
        popularity = artist.popularity,
        firstFollowedAt = Some(firstFollowedAt),
        lastSeenAt = Some(lastSeenAt),
        lastSyncedAt = Some(lastSyncedAt),
        createdAt = lastSyncedAt,
        updatedAt = lastSyncedAt,
        deletedAt = Option.empty,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserFollowedArtistDbRow
  }
}
