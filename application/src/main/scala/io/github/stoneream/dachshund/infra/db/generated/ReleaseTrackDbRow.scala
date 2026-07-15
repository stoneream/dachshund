package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class ReleaseTrackDbRow(
    id: Long,
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
    syncedAt: Option[LocalDateTime],
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
