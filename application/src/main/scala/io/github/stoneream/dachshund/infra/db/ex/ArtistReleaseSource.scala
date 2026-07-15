package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

import java.time.LocalDateTime

final case class ArtistReleaseSource(
    spotifyReleaseCode: String,
    sourceSpotifyArtistCode: String,
    releaseName: String,
    releaseType: String,
    albumType: String,
    albumGroup: Option[String],
    spotifyReleaseUri: String,
    spotifyUrl: String,
    href: String,
    primaryImageUrl: String,
    primaryImageHeight: Option[Int],
    primaryImageWidth: Option[Int],
    imagesJson: Option[String],
    releaseDateText: String,
    releaseDatePrecision: String,
    releaseDateAt: Option[LocalDateTime],
    totalTracksCount: Option[Int],
    labelName: Option[String],
    normalizedLabelName: Option[String],
    externalIdsJson: Option[String],
    upcCode: Option[String],
    eanCode: Option[String],
    isrcCode: Option[String],
    copyrightsJson: Option[String],
    availableMarketsJson: Option[String],
    genresJson: Option[String],
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
