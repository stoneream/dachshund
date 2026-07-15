package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserSpotifyAuthDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.user_profile_client.SpotifyUserProfileClient.CurrentUserProfile

object UserSpotifyAuthDbRowSyntax {

  extension (source: UserSpotifyAuthSource) {
    def toUserSpotifyAuthDbRow: UserSpotifyAuthDbRow = {
      import DbRowValues.*

      UserSpotifyAuthDbRow(
        id = 0L,
        userId = source.userId,
        spotifyUserId = source.spotifyUserId,
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

  extension (profile: CurrentUserProfile) {
    def toUserSpotifyAuthDbRow(userId: Long, now: BusinessDateTime): UserSpotifyAuthDbRow =
      UserSpotifyAuthSource(
        userId = userId,
        spotifyUserId = profile.id,
        createdAt = now,
        updatedAt = now,
        deletedAt = Option.empty,
        createdUser = AuditUser.User(userId),
        updatedUser = AuditUser.User(userId),
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserSpotifyAuthDbRow
  }
}
