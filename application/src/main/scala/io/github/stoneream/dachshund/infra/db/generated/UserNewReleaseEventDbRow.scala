package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserNewReleaseEventDbRow(
    id: Long,
    userId: Long,
    artistReleaseId: Long,
    spotifyReleaseCode: String,
    sourceSpotifyArtistCode: String,
    detectedAt: LocalDateTime,
    detectionSyncCode: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
