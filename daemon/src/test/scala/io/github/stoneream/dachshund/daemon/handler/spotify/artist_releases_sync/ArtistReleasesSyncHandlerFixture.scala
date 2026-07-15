package io.github.stoneream.dachshund.daemon.handler.spotify.artist_releases_sync

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSource, ArtistReleaseSyncQueueSource, UserFollowedArtistSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.UserFollowedArtistDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.client.model.{SpotifyArtistRelease, SpotifyArtistReleasePage, SpotifyReleaseTrack}

import java.time.LocalDate

object ArtistReleasesSyncHandlerFixture {
  val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  val ActiveUserRow = UserSource(
    userName = "artist-release-sync-user",
    displayName = "Artist Release Sync User",
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

  def pageArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-page", "Page Artist")

  def finalPageArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-final", "Final Page Artist")

  def existingReleaseArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-existing", "Existing Release Artist")

  def unauthorizedArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-unauthorized", "Unauthorized Artist")

  def invalidClientArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-invalid-client", "Invalid Client Artist")

  def rateLimitedArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-rate-limited", "Rate Limited Artist")

  def unexpectedFailureFirstArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-unexpected-first", "Unexpected Failure First Artist")

  def unexpectedFailureSecondArtistFollowedArtistRow(userId: Long): UserFollowedArtistDbRow =
    followedArtistRow(userId, "artist-unexpected-second", "Unexpected Failure Second Artist")

  val PageQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-page",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 10,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 0,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val FinalPageQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-final",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 20,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 0,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val ExistingReleaseQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-existing",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 10,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 0,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val UnauthorizedQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-unauthorized",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 0,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 0,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val InvalidClientQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-invalid-client",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 0,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 0,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val RateLimitedQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-rate-limited",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 0,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 2,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val UnexpectedFailureFirstQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-unexpected-first",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 0,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 0,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val UnexpectedFailureSecondQueueRow = ArtistReleaseSyncQueueSource(
    spotifyArtistCode = "artist-unexpected-second",
    syncScope = "INCREMENTAL",
    status = QueueJobStatus.Scheduled,
    includeGroups = "album,single",
    market = Some("JP"),
    requestedLimit = 10,
    nextOffset = 0,
    nextAttemptAt = Some(fixedNow),
    lastAttemptedAt = None,
    completedAt = None,
    attemptCount = 0,
    lastFailedAt = None,
    lastErrorType = "",
    lockToken = "",
    lockedUntil = None,
    createdAt = fixedNow,
    updatedAt = fixedNow,
    deletedAt = None,
    createdUser = AuditUser.System,
    updatedUser = AuditUser.System,
    deletedUser = AuditUser.Empty,
    deleted = 0L,
    lockVersion = 0L
  ).toArtistReleaseSyncQueueDbRow

  val ExistingReleaseRow = ArtistReleaseSource(
    spotifyReleaseCode = "release-existing",
    sourceSpotifyArtistCode = "artist-existing",
    releaseName = "Existing Release",
    releaseType = "ALBUM",
    albumType = "album",
    albumGroup = Some("album"),
    spotifyReleaseUri = "spotify:album:release-existing",
    spotifyUrl = "https://open.spotify.com/album/release-existing",
    href = "https://api.spotify.com/v1/albums/release-existing",
    primaryImageUrl = "",
    primaryImageHeight = None,
    primaryImageWidth = None,
    imagesJson = None,
    releaseDateText = "2026-06-20",
    releaseDatePrecision = "day",
    releaseDateAt = Some(LocalDate.of(2026, 6, 20).atStartOfDay()),
    totalTracksCount = Some(1),
    labelName = Some("Existing Label"),
    normalizedLabelName = Some("existing label"),
    externalIdsJson = None,
    upcCode = None,
    eanCode = None,
    isrcCode = None,
    copyrightsJson = None,
    availableMarketsJson = None,
    genresJson = None,
    restrictionsJson = None,
    popularity = Some(50),
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

  val PageWithNextOffset: SpotifyArtistReleasePage = SpotifyArtistReleasePage(
    releases = Seq(
      artistRelease(
        spotifyReleaseCode = "release-page",
        spotifyArtistCode = "artist-page",
        releaseName = "Page Release",
        tracks = Seq(
          releaseTrack("release-page-track-1"),
          releaseTrack("release-page-track-2")
        )
      )
    ),
    nextOffset = Some(20)
  )

  val FinalPage: SpotifyArtistReleasePage = SpotifyArtistReleasePage(
    releases = Seq(
      artistRelease(
        spotifyReleaseCode = "release-final",
        spotifyArtistCode = "artist-final",
        releaseName = "Final Release",
        tracks = Seq(releaseTrack("release-final-track-1"))
      )
    ),
    nextOffset = None
  )

  val ExistingReleasePage: SpotifyArtistReleasePage = SpotifyArtistReleasePage(
    releases = Seq(
      artistRelease(
        spotifyReleaseCode = "release-existing",
        spotifyArtistCode = "artist-existing",
        releaseName = "Existing Release",
        tracks = Seq(releaseTrack("release-existing-track-1"))
      )
    ),
    nextOffset = Some(20)
  )

  val UnauthorizedRetryPage: SpotifyArtistReleasePage = SpotifyArtistReleasePage(
    releases = Seq(
      artistRelease(
        spotifyReleaseCode = "release-unauthorized-retry",
        spotifyArtistCode = "artist-unauthorized",
        releaseName = "Unauthorized Retry Release",
        tracks = Seq(releaseTrack("release-unauthorized-retry-track-1"))
      )
    ),
    nextOffset = None
  )

  val PageWithDuplicateTracks: SpotifyArtistReleasePage = SpotifyArtistReleasePage(
    releases = Seq(
      artistRelease(
        spotifyReleaseCode = "release-unexpected-first",
        spotifyArtistCode = "artist-unexpected-first",
        releaseName = "Unexpected First Release",
        tracks = Seq(
          releaseTrack("duplicated-track"),
          releaseTrack("duplicated-track")
        )
      )
    ),
    nextOffset = None
  )

  val UnexpectedFailureSecondPage: SpotifyArtistReleasePage = SpotifyArtistReleasePage(
    releases = Seq(
      artistRelease(
        spotifyReleaseCode = "release-unexpected-second",
        spotifyArtistCode = "artist-unexpected-second",
        releaseName = "Unexpected Second Release",
        tracks = Seq(releaseTrack("release-unexpected-second-track-1"))
      )
    ),
    nextOffset = None
  )

  private def followedArtistRow(
      userId: Long,
      spotifyArtistCode: String,
      artistName: String
  ) =
    UserFollowedArtistSource(
      userId = userId,
      spotifyArtistCode = spotifyArtistCode,
      artistName = artistName,
      spotifyArtistUri = s"spotify:artist:$spotifyArtistCode",
      spotifyUrl = s"https://open.spotify.com/artist/$spotifyArtistCode",
      href = s"https://api.spotify.com/v1/artists/$spotifyArtistCode",
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

  private def artistRelease(
      spotifyReleaseCode: String,
      spotifyArtistCode: String,
      releaseName: String,
      tracks: Seq[SpotifyReleaseTrack]
  ): SpotifyArtistRelease =
    SpotifyArtistRelease(
      spotifyReleaseCode = spotifyReleaseCode,
      sourceSpotifyArtistCode = spotifyArtistCode,
      releaseName = releaseName,
      releaseType = "ALBUM",
      albumType = "album",
      albumGroup = Some("album"),
      spotifyReleaseUri = s"spotify:album:$spotifyReleaseCode",
      spotifyUrl = s"https://open.spotify.com/album/$spotifyReleaseCode",
      href = s"https://api.spotify.com/v1/albums/$spotifyReleaseCode",
      primaryImageUrl = "",
      primaryImageHeight = None,
      primaryImageWidth = None,
      imagesJson = None,
      releaseDateText = "2026-06-20",
      releaseDatePrecision = "day",
      releaseDateAt = Some(LocalDate.of(2026, 6, 20).atStartOfDay()),
      totalTracksCount = Some(tracks.size),
      labelName = Some("Label"),
      normalizedLabelName = Some("label"),
      externalIdsJson = None,
      upcCode = None,
      eanCode = None,
      isrcCode = None,
      copyrightsJson = None,
      availableMarketsJson = None,
      genresJson = None,
      restrictionsJson = None,
      popularity = Some(50),
      tracks = tracks
    )

  private def releaseTrack(spotifyTrackCode: String): SpotifyReleaseTrack =
    SpotifyReleaseTrack(
      spotifyTrackCode = spotifyTrackCode,
      trackName = s"Track $spotifyTrackCode",
      spotifyTrackUri = s"spotify:track:$spotifyTrackCode",
      spotifyUrl = s"https://open.spotify.com/track/$spotifyTrackCode",
      href = s"https://api.spotify.com/v1/tracks/$spotifyTrackCode",
      discNumber = 1,
      trackNumber = 1,
      durationMs = Some(180000),
      explicit = Some(0L),
      isPlayable = Some(1L),
      isLocal = None,
      linkedFromSpotifyTrackCode = None,
      linkedFromSpotifyTrackUri = None,
      previewUrl = None,
      externalIdsJson = None,
      isrcCode = None,
      eanCode = None,
      upcCode = None,
      availableMarketsJson = None,
      restrictionsJson = None,
      popularity = None
    )
}
