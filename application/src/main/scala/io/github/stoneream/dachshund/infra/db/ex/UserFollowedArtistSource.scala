package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserFollowedArtistSource(
    id: Long = 0L,
    userId: Long,
    spotifyArtistCode: String,
    artistName: String,
    spotifyArtistUri: String,
    spotifyUrl: String,
    href: String,
    primaryImageUrl: String,
    primaryImageHeight: Option[Int],
    primaryImageWidth: Option[Int],
    imagesJson: Option[String],
    genresJson: Option[String],
    followersTotal: Option[Long],
    popularity: Option[Int],
    firstFollowedAt: Option[BusinessDateTime],
    lastSeenAt: Option[BusinessDateTime],
    lastSyncedAt: Option[BusinessDateTime],
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
