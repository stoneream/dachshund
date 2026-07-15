package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.UserDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.user_profile_client.SpotifyUserProfileClient.CurrentUserProfile

object UserDbRowSyntax {

  extension (source: UserSource) {
    def toUserDbRow: UserDbRow = {
      import DbRowValues.*

      UserDbRow(
        id = 0L,
        userName = source.userName,
        displayName = source.displayName,
        timeZone = source.timeZone,
        enabled = source.enabled,
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
    def toUserDbRow(now: BusinessDateTime): UserDbRow =
      UserSource(
        userName = s"spotify:${profile.id}",
        displayName = profile.displayName.map(_.trim).filter(_.nonEmpty).getOrElse(profile.id),
        timeZone = "Asia/Tokyo",
        enabled = 1L,
        createdAt = now,
        updatedAt = now,
        deletedAt = Option.empty,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserDbRow
  }
}
