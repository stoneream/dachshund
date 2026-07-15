package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class UserFollowedArtistDbRow(
    id: Long,
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
    firstFollowedAt: Option[LocalDateTime],
    lastSeenAt: Option[LocalDateTime],
    lastSyncedAt: Option[LocalDateTime],
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
