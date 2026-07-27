package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserNewReleaseEventSource(
    id: Long = 0L,
    userId: Long,
    artistReleaseId: Long,
    spotifyReleaseCode: String,
    sourceSpotifyArtistCode: String,
    detectedAt: BusinessDateTime,
    detectionSyncCode: String,
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
