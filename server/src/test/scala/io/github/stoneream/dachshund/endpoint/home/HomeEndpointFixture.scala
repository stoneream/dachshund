package io.github.stoneream.dachshund.endpoint.home

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseEventDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSessionTokenDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSource, UserFollowedArtistSource, UserNewReleaseEventSource, UserSessionTokenSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.{UserFollowedArtistDbRow, UserNewReleaseEventDbRow, UserSessionTokenDbRow}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService.IssuedSessionToken
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

import java.time.LocalDate

object HomeEndpointFixture {
  val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-07-08T12:00:00+09:00")
  val SourceArtistCode = "source-artist-code"

  val UserRow = UserSource(
    userName = "user-name",
    displayName = "display user",
    timeZone = "Asia/Tokyo",
    enabled = 1L,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toUserDbRow

  val JuneReleaseRow = ArtistReleaseSource(
    spotifyReleaseCode = "spotify-release-june",
    sourceSpotifyArtistCode = SourceArtistCode,
    releaseName = "June Release",
    releaseType = "ALBUM",
    albumType = "album",
    albumGroup = Some("album"),
    spotifyReleaseUri = "",
    spotifyUrl = "",
    href = "",
    primaryImageUrl = "",
    primaryImageHeight = None,
    primaryImageWidth = None,
    imagesJson = None,
    releaseDateText = "2026-06-30",
    releaseDatePrecision = "day",
    releaseDateAt = Some(LocalDate.of(2026, 6, 30).atStartOfDay()),
    totalTracksCount = None,
    labelName = Some("Label Name"),
    normalizedLabelName = Some("label name"),
    externalIdsJson = None,
    upcCode = None,
    eanCode = None,
    isrcCode = None,
    copyrightsJson = None,
    availableMarketsJson = None,
    genresJson = None,
    restrictionsJson = None,
    popularity = None,
    syncedAt = Some(fixedNow),
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseDbRow

  val JulyFirstReleaseRow = ArtistReleaseSource(
    spotifyReleaseCode = "spotify-release-july-1",
    sourceSpotifyArtistCode = SourceArtistCode,
    releaseName = "July First Release",
    releaseType = "ALBUM",
    albumType = "album",
    albumGroup = Some("album"),
    spotifyReleaseUri = "",
    spotifyUrl = "https://open.spotify.com/album/spotify-release-july-2",
    href = "",
    primaryImageUrl = "",
    primaryImageHeight = None,
    primaryImageWidth = None,
    imagesJson = None,
    releaseDateText = "2026-07-01",
    releaseDatePrecision = "day",
    releaseDateAt = Some(LocalDate.of(2026, 7, 1).atStartOfDay()),
    totalTracksCount = None,
    labelName = Some("Label Name"),
    normalizedLabelName = Some("label name"),
    externalIdsJson = None,
    upcCode = None,
    eanCode = None,
    isrcCode = None,
    copyrightsJson = None,
    availableMarketsJson = None,
    genresJson = None,
    restrictionsJson = None,
    popularity = None,
    syncedAt = Some(fixedNow),
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseDbRow

  val JulySecondReleaseRow = ArtistReleaseSource(
    spotifyReleaseCode = "spotify-release-july-2",
    sourceSpotifyArtistCode = SourceArtistCode,
    releaseName = "July Second Release",
    releaseType = "ALBUM",
    albumType = "album",
    albumGroup = Some("album"),
    spotifyReleaseUri = "",
    spotifyUrl = "",
    href = "",
    primaryImageUrl = "",
    primaryImageHeight = None,
    primaryImageWidth = None,
    imagesJson = None,
    releaseDateText = "2026-07-06",
    releaseDatePrecision = "day",
    releaseDateAt = Some(LocalDate.of(2026, 7, 6).atStartOfDay()),
    totalTracksCount = None,
    labelName = Some("Label Name"),
    normalizedLabelName = Some("label name"),
    externalIdsJson = None,
    upcCode = None,
    eanCode = None,
    isrcCode = None,
    copyrightsJson = None,
    availableMarketsJson = None,
    genresJson = None,
    restrictionsJson = None,
    popularity = None,
    syncedAt = Some(fixedNow),
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseDbRow

  def followedArtistRow(userId: Long): UserFollowedArtistDbRow = UserFollowedArtistSource(
    userId = userId,
    spotifyArtistCode = SourceArtistCode,
    artistName = "Artist Name",
    spotifyArtistUri = "",
    spotifyUrl = "",
    href = "",
    primaryImageUrl = "",
    primaryImageHeight = None,
    primaryImageWidth = None,
    imagesJson = None,
    genresJson = None,
    followersTotal = None,
    popularity = None,
    firstFollowedAt = Some(fixedNow),
    lastSeenAt = Some(fixedNow),
    lastSyncedAt = Some(fixedNow),
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toUserFollowedArtistDbRow

  def juneEventRow(userId: Long, artistReleaseId: Long): UserNewReleaseEventDbRow = UserNewReleaseEventSource(
    userId = userId,
    artistReleaseId = artistReleaseId,
    spotifyReleaseCode = JuneReleaseRow.spotifyReleaseCode,
    sourceSpotifyArtistCode = SourceArtistCode,
    detectedAt = fixedNow,
    detectionSyncCode = "test-sync",
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toUserNewReleaseEventDbRow

  def julyFirstEventRow(userId: Long, artistReleaseId: Long): UserNewReleaseEventDbRow = UserNewReleaseEventSource(
    userId = userId,
    artistReleaseId = artistReleaseId,
    spotifyReleaseCode = JulyFirstReleaseRow.spotifyReleaseCode,
    sourceSpotifyArtistCode = SourceArtistCode,
    detectedAt = fixedNow,
    detectionSyncCode = "test-sync",
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toUserNewReleaseEventDbRow

  def julySecondEventRow(userId: Long, artistReleaseId: Long): UserNewReleaseEventDbRow = UserNewReleaseEventSource(
    userId = userId,
    artistReleaseId = artistReleaseId,
    spotifyReleaseCode = JulySecondReleaseRow.spotifyReleaseCode,
    sourceSpotifyArtistCode = SourceArtistCode,
    detectedAt = fixedNow,
    detectionSyncCode = "test-sync",
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toUserNewReleaseEventDbRow

  def userSessionTokenRow(userId: Long, issuedSessionToken: IssuedSessionToken): UserSessionTokenDbRow =
    UserSessionTokenSource(
      userId = userId,
      hashedToken = issuedSessionToken.hashedToken,
      issuedAt = issuedSessionToken.issuedAt,
      lastAccessedAt = issuedSessionToken.lastAccessedAt,
      idleExpiresAt = issuedSessionToken.idleExpiresAt,
      expiresAt = issuedSessionToken.expiresAt,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserSessionTokenDbRow
}
