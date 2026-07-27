package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class ReleaseTrackSource(
    id: Long = 0L,
    artistReleaseId: Long,
    spotifyTrackCode: String,
    trackName: String,
    spotifyTrackUri: String,
    spotifyUrl: String,
    href: String,
    discNumber: Int,
    trackNumber: Int,
    durationMs: Option[Int],
    explicit: Option[Long],
    isPlayable: Option[Long],
    isLocal: Option[Long],
    linkedFromSpotifyTrackCode: Option[String],
    linkedFromSpotifyTrackUri: Option[String],
    previewUrl: Option[String],
    externalIdsJson: Option[String],
    isrcCode: Option[String],
    eanCode: Option[String],
    upcCode: Option[String],
    availableMarketsJson: Option[String],
    restrictionsJson: Option[String],
    popularity: Option[Int],
    syncedAt: Option[BusinessDateTime],
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
