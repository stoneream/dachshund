package io.github.stoneream.dachshund.daemon.handler.spotify

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthorizationRefreshQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{UserSource, UserSpotifyAuthorizationRefreshQueueSource, UserSpotifyAuthorizationSource}
import io.github.stoneream.dachshund.infra.db.generated.{UserSpotifyAuthorizationDbRow, UserSpotifyAuthorizationRefreshQueueDbRow}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.EncryptedSpotifyToken
import io.github.stoneream.dachshund.model.QueueJobStatus

import scala.concurrent.duration.*

object SpotifyAccessTokenRefreshJobHandlerFixture {
  val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  val StaleProcessingUserRow =
    userRow("refresh-stale-processing-user", "Refresh Stale Processing User")

  val SuccessFirstUserRow =
    userRow("refresh-success-first-user", "Refresh Success First User")

  val SuccessSecondUserRow =
    userRow("refresh-success-second-user", "Refresh Success Second User")

  val DecryptFailureUserRow =
    userRow("refresh-decrypt-failure-user", "Refresh Decrypt Failure User")

  val InvalidClientUserRow =
    userRow("refresh-invalid-client-user", "Refresh Invalid Client User")

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

  def staleProcessingRefreshQueueRow(authorizationId: Long): UserSpotifyAuthorizationRefreshQueueDbRow =
    UserSpotifyAuthorizationRefreshQueueSource(
      authorizationId = authorizationId,
      status = QueueJobStatus.Processing,
      nextAttemptAt = Some(fixedNow),
      attemptCount = 0,
      lastFailedAt = None,
      lastErrorType = "",
      lockToken = "stale-lock-token",
      lockedUntil = Some(fixedNow.minus(1.minute)),
      lastAttemptedAt = Some(fixedNow.minus(1.hour)),
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

  def successFirstRefreshQueueRow(authorizationId: Long): UserSpotifyAuthorizationRefreshQueueDbRow =
    scheduledRefreshQueueRow(authorizationId)

  def successSecondRefreshQueueRow(authorizationId: Long): UserSpotifyAuthorizationRefreshQueueDbRow =
    scheduledRefreshQueueRow(authorizationId)

  def decryptFailureRefreshQueueRow(authorizationId: Long): UserSpotifyAuthorizationRefreshQueueDbRow =
    scheduledRefreshQueueRow(authorizationId)

  def invalidClientRefreshQueueRow(authorizationId: Long): UserSpotifyAuthorizationRefreshQueueDbRow =
    scheduledRefreshQueueRow(authorizationId)

  private def scheduledRefreshQueueRow(authorizationId: Long) =
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
}
