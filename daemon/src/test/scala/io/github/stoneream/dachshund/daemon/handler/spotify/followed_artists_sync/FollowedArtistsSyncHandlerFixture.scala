package io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.FollowedArtistSyncQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationRefreshQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{FollowedArtistSyncQueueSource, UserFollowedArtistSource, UserSource, UserSpotifyAuthorizationRefreshQueueSource, UserSpotifyAuthorizationSource}
import io.github.stoneream.dachshund.infra.db.generated.{FollowedArtistSyncQueueDbRow, UserFollowedArtistDbRow, UserSpotifyAuthorizationDbRow, UserSpotifyAuthorizationRefreshQueueDbRow}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.client.model.{SpotifyFollowedArtist, SpotifyFollowedArtistsPage}

import scala.concurrent.duration.*

object FollowedArtistsSyncHandlerFixture {
  val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")
  val syncDateMarker: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T00:00:00+09:00")
  val oldSeenAt: BusinessDateTime =
    BusinessDateTime.from("2026-06-20T00:00:00+09:00")

  private val syncDate = fixedNow.toLocalDate

  val AllPagesUserRow =
    userRow("followed-all-pages-user", "Followed All Pages User")

  val DeletionUserRow =
    userRow("followed-deletion-user", "Followed Deletion User")

  val UnauthorizedUserRow =
    userRow("followed-unauthorized-user", "Followed Unauthorized User")

  val ForbiddenUserRow =
    userRow("followed-forbidden-user", "Followed Forbidden User")

  val RateLimitedUserRow =
    userRow("followed-rate-limited-user", "Followed Rate Limited User")

  val CursorRateLimitedUserRow =
    userRow("followed-cursor-rate-limited-user", "Followed Cursor Rate Limited User")

  val UnexpectedFailureFirstUserRow =
    userRow("followed-unexpected-first-user", "Followed Unexpected First User")

  val UnexpectedFailureSecondUserRow =
    userRow("followed-unexpected-second-user", "Followed Unexpected Second User")

  def authorizationRow(
      userId: Long,
      encryptedAccessToken: EncryptedSpotifyToken,
      encryptedRefreshToken: EncryptedSpotifyToken
  ): UserSpotifyAuthorizationDbRow =
    UserSpotifyAuthorizationSource(
      userId = userId,
      scopeText = "user-follow-read",
      accessTokenCipher = encryptedAccessToken.cipherText,
      accessTokenNonce = encryptedAccessToken.nonce,
      accessTokenTag = encryptedAccessToken.tag,
      refreshTokenCipher = encryptedRefreshToken.cipherText,
      refreshTokenNonce = encryptedRefreshToken.nonce,
      refreshTokenTag = encryptedRefreshToken.tag,
      encryptionAlgorithm = encryptedAccessToken.algorithm,
      encryptionKeyVersion = encryptedAccessToken.keyVersion,
      tokenType = "Bearer",
      accessTokenExpiresAt = fixedNow.plus(3600.seconds),
      refreshMarginSeconds = 300,
      lastAuthorizedAt = Some(fixedNow),
      lastRefreshedAt = None,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserSpotifyAuthorizationDbRow

  def authorizationRefreshQueueRow(authorizationId: Long): UserSpotifyAuthorizationRefreshQueueDbRow =
    UserSpotifyAuthorizationRefreshQueueSource(
      authorizationId = authorizationId,
      status = QueueJobStatus.Scheduled,
      nextAttemptAt = Some(fixedNow),
      attemptCount = 0,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "",
      lockedUntil = None,
      lastAttemptedAt = None,
      completedAt = None,
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = 0L
    ).toUserSpotifyAuthorizationRefreshQueueDbRow

  def allPagesQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = Some("current-cursor"), attemptCount = 0)

  def deletionQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = None, attemptCount = 0)

  def unauthorizedQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = None, attemptCount = 0)

  def forbiddenQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = None, attemptCount = 0)

  def rateLimitedQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = None, attemptCount = 2)

  def cursorRateLimitedQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = None, attemptCount = 2)

  def unexpectedFailureFirstQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = None, attemptCount = 0)

  def unexpectedFailureSecondQueueRow(userId: Long): FollowedArtistSyncQueueDbRow =
    followedQueueRow(userId = userId, afterCursor = None, attemptCount = 0)

  def deletionOldArtistFirstRow(userId: Long): UserFollowedArtistDbRow =
    existingFollowedArtistRow(userId, "artist-deleted-1", "Deleted Artist 1", lockVersion = 1L)

  def deletionOldArtistSecondRow(userId: Long): UserFollowedArtistDbRow =
    existingFollowedArtistRow(userId, "artist-deleted-2", "Deleted Artist 2", lockVersion = 2L)

  def deletionOldArtistThirdRow(userId: Long): UserFollowedArtistDbRow =
    existingFollowedArtistRow(userId, "artist-deleted-3", "Deleted Artist 3", lockVersion = 3L)

  val AllPagesFirstPage: SpotifyFollowedArtistsPage = SpotifyFollowedArtistsPage(
    artists = Seq(followedArtist("artist-all-pages-1", "All Pages Artist 1")),
    nextAfterCursor = Some("next-cursor")
  )

  val AllPagesSecondPage: SpotifyFollowedArtistsPage = SpotifyFollowedArtistsPage(
    artists = Seq(followedArtist("artist-all-pages-2", "All Pages Artist 2")),
    nextAfterCursor = None
  )

  val DeletionPage: SpotifyFollowedArtistsPage = SpotifyFollowedArtistsPage(
    artists = Seq(followedArtist("artist-kept", "Kept Artist")),
    nextAfterCursor = None
  )

  val UnauthorizedRetryPage: SpotifyFollowedArtistsPage = SpotifyFollowedArtistsPage(
    artists = Seq(followedArtist("artist-unauthorized-retry", "Unauthorized Retry Artist")),
    nextAfterCursor = None
  )

  val CursorRateLimitedFirstPage: SpotifyFollowedArtistsPage = SpotifyFollowedArtistsPage(
    artists = Seq(followedArtist("artist-cursor-first", "Cursor First Artist")),
    nextAfterCursor = Some("next-cursor")
  )

  val UnexpectedFailurePage: SpotifyFollowedArtistsPage = SpotifyFollowedArtistsPage(
    artists = Seq(
      SpotifyFollowedArtist(
        spotifyArtistCode = "artist-unexpected-failure",
        artistName = null,
        spotifyArtistUri = "spotify:artist:artist-unexpected-failure",
        spotifyUrl = "https://open.spotify.com/artist/artist-unexpected-failure",
        href = "https://api.spotify.com/v1/artists/artist-unexpected-failure",
        primaryImageUrl = "",
        primaryImageHeight = None,
        primaryImageWidth = None,
        imagesJson = None,
        genresJson = None,
        followersTotal = Some(10L),
        popularity = Some(20)
      )
    ),
    nextAfterCursor = None
  )

  private def userRow(userName: String, displayName: String) =
    UserSource(
      userName = userName,
      displayName = displayName,
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

  private def followedQueueRow(
      userId: Long,
      afterCursor: Option[String],
      attemptCount: Int
  ) =
    FollowedArtistSyncQueueSource(
      userId = userId,
      syncDate = syncDate,
      status = QueueJobStatus.Scheduled,
      requestedLimit = 50,
      afterCursor = afterCursor,
      nextAttemptAt = Some(fixedNow),
      lastAttemptedAt = None,
      completedAt = None,
      attemptCount = attemptCount,
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
    ).toFollowedArtistSyncQueueDbRow

  private def existingFollowedArtistRow(
      userId: Long,
      spotifyArtistCode: String,
      artistName: String,
      lockVersion: Long
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
      followersTotal = Some(10L),
      popularity = Some(20),
      firstFollowedAt = Some(oldSeenAt),
      lastSeenAt = Some(oldSeenAt),
      lastSyncedAt = Some(oldSeenAt),
      createdAt = fixedNow,
      updatedAt = fixedNow,
      deletedAt = None,
      createdUser = AuditUser.System,
      updatedUser = AuditUser.System,
      deletedUser = AuditUser.Empty,
      deleted = 0L,
      lockVersion = lockVersion
    ).toUserFollowedArtistDbRow

  private def followedArtist(spotifyArtistCode: String, artistName: String): SpotifyFollowedArtist =
    SpotifyFollowedArtist(
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
      followersTotal = Some(10L),
      popularity = Some(20)
    )
}
