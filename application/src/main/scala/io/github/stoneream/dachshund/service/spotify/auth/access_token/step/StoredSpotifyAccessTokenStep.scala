package io.github.stoneream.dachshund.service.spotify.auth.access_token.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.{EncryptedSpotifyToken, SpotifyTokenEncryptionAad, SpotifyTokenEncryptionException, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProvider.ResolvedSpotifyAuthorizationCodeAccessToken
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as ProviderException
import io.github.stoneream.dachshund.service.spotify.auth.access_token.context.{SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshFailureReason, SpotifyAccessTokenRefreshedTokens}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.model.SpotifyAccessTokenResolveTarget

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.access_token.SpotifyAccessTokenReader
import io.github.stoneream.dachshund.infra.db.writer.SpotifyAccessTokenWriter
import io.github.stoneream.dachshund.model.QueueJobStatus
import scala.concurrent.Future
import scala.util.control.NonFatal

/** 保存済み Spotify access token の取得、復号、refresh 結果の永続化を行う。 */
@Singleton
class StoredSpotifyAccessTokenStep @Inject() (
    applicationConfig: ApplicationConfig,
    databaseTransaction: DatabaseTransaction,
    accessTokenReader: SpotifyAccessTokenReader,
    accessTokenWriter: SpotifyAccessTokenWriter,
    spotifyTokenEncryptor: SpotifyTokenEncryptor,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) {
  def findResolveTarget(userId: Long): Future[SpotifyAccessTokenResolveTarget] = {
    given DefaultExecutor = defaultExecutor

    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        accessTokenReader.findResolveTargetByUserId(userId)
      }
    }(using databaseExecutor).flatMap {
      case Some(target) =>
        Future.successful(target)
      case None =>
        Future.failed(ProviderException.AuthorizationNotFound(userId))
    }
  }

  def decryptAccessToken(
      target: SpotifyAccessTokenResolveTarget,
      now: BusinessDateTime
  ): Future[String] =
    decrypt(
      target = target,
      now = now,
      aad = SpotifyTokenEncryptionAad.accessToken(target.userId, target.encryptedAccessToken.keyVersion),
      token = target.encryptedAccessToken
    )

  def decryptRefreshToken(
      target: SpotifyAccessTokenResolveTarget,
      now: BusinessDateTime
  ): Future[String] =
    decrypt(
      target = target,
      now = now,
      aad = SpotifyTokenEncryptionAad.refreshToken(target.userId, target.encryptedRefreshToken.keyVersion),
      token = target.encryptedRefreshToken
    )

  def persistRefreshSuccess(
      target: SpotifyAccessTokenResolveTarget,
      refreshedTokens: SpotifyAccessTokenRefreshedTokens,
      now: BusinessDateTime
  ): Future[ResolvedSpotifyAuthorizationCodeAccessToken] = {
    given DefaultExecutor = defaultExecutor

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        accessTokenWriter.markRefreshSucceeded(
          authorizationId = target.authorizationId,
          queueId = target.queueId,
          scopeText = refreshedTokens.scopeText,
          accessTokenCipher = refreshedTokens.encryptedAccessToken.cipherText,
          accessTokenNonce = refreshedTokens.encryptedAccessToken.nonce,
          accessTokenTag = refreshedTokens.encryptedAccessToken.tag,
          refreshTokenCipher = refreshedTokens.encryptedRefreshToken.cipherText,
          refreshTokenNonce = refreshedTokens.encryptedRefreshToken.nonce,
          refreshTokenTag = refreshedTokens.encryptedRefreshToken.tag,
          encryptionAlgorithm = refreshedTokens.encryptedAccessToken.algorithm,
          encryptionKeyVersion = refreshedTokens.encryptedAccessToken.keyVersion,
          tokenType = refreshedTokens.tokenType,
          accessTokenExpiresAt = refreshedTokens.accessTokenExpiresAt,
          lastRefreshedAt = now,
          authorizationUpdatedAt = now,
          authorizationUpdatedUser = AuditUser.System,
          authorizationLockVersion = target.authorizationLockVersion + 1L,
          queueStatus = QueueJobStatus.Scheduled,
          nextAttemptAt = refreshedTokens.nextRefreshAttemptAt,
          attemptCount = 0,
          lastFailedAt = Option.empty,
          lastErrorType = "",
          lastAttemptedAt = now,
          completedAt = now,
          lockToken = "",
          lockedUntil = Option.empty,
          queueUpdatedAt = now,
          queueUpdatedUser = AuditUser.System,
          queueDeletedAt = Option.empty,
          queueDeletedUser = AuditUser.Empty,
          queueDeleted = 0L,
          queueLockVersion = target.queueLockVersion + 1L,
          expectedAuthorizationLockVersion = target.authorizationLockVersion,
          expectedQueueStatus = QueueJobStatus.Scheduled,
          expectedQueueLockVersion = target.queueLockVersion
        )
      }
    }(using databaseExecutor).flatMap {
      case true =>
        Future.successful(
          ResolvedSpotifyAuthorizationCodeAccessToken(
            accessToken = refreshedTokens.accessToken,
            tokenType = refreshedTokens.tokenType,
            scopeText = refreshedTokens.scopeText,
            expiresAt = refreshedTokens.accessTokenExpiresAt
          )
        )
      case false =>
        Future.failed(ProviderException.ConcurrentUpdate(target.userId))
    }
  }

  def persistRefreshFailure(
      target: SpotifyAccessTokenResolveTarget,
      failure: SpotifyAccessTokenRefreshFailure,
      now: BusinessDateTime,
      cause: Option[Throwable] = None
  ): Future[Nothing] = {
    given DefaultExecutor = defaultExecutor

    if (SpotifyAccessTokenRefreshFailureClassifier.requiresReauthorization(failure.reason)) {
      markReauthorizationRequired(target, failure.reason.dbValue, now, cause)
    } else if (SpotifyAccessTokenRefreshFailureClassifier.isTemporaryFailure(failure.reason)) {
      markTemporaryFailure(target, failure, now, cause)
    } else {
      Future.failed(ProviderException.Unknown(cause.orNull))
    }
  }

  private def decrypt(
      target: SpotifyAccessTokenResolveTarget,
      now: BusinessDateTime,
      aad: String,
      token: EncryptedSpotifyToken
  ): Future[String] =
    try {
      Future.successful(spotifyTokenEncryptor.decrypt(token, Some(aad)))
    } catch {
      case _: SpotifyTokenEncryptionException =>
        persistRefreshFailure(target, SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.TokenDecryptFailed), now)
      case NonFatal(_) =>
        persistRefreshFailure(target, SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.TokenDecryptFailed), now)
    }

  private def markReauthorizationRequired(
      target: SpotifyAccessTokenResolveTarget,
      reasonType: String,
      now: BusinessDateTime,
      cause: Option[Throwable]
  )(using DefaultExecutor): Future[Nothing] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        accessTokenWriter.markRefreshFailed(
          authorizationId = target.authorizationId,
          queueId = target.queueId,
          queueStatus = QueueJobStatus.Blocked,
          attemptCount = target.attemptCount,
          nextAttemptAt = Option.empty,
          lastFailedAt = now,
          lastErrorType = reasonType,
          lastAttemptedAt = now,
          lockToken = "",
          lockedUntil = Option.empty,
          updatedAt = now,
          updatedUser = AuditUser.System,
          lockVersion = target.queueLockVersion + 1L,
          expectedQueueStatus = QueueJobStatus.Scheduled,
          expectedQueueLockVersion = target.queueLockVersion
        )
      }
    }(using databaseExecutor).flatMap {
      case true =>
        Future.failed(ProviderException.ReauthorizationRequired(target.userId, reasonType, cause.orNull))
      case false =>
        Future.failed(ProviderException.ConcurrentUpdate(target.userId))
    }

  private def markTemporaryFailure(
      target: SpotifyAccessTokenResolveTarget,
      failure: SpotifyAccessTokenRefreshFailure,
      now: BusinessDateTime,
      cause: Option[Throwable]
  )(using DefaultExecutor): Future[Nothing] = {
    val nextAttemptAt = CalculateNextSpotifyAccessTokenRefreshAttemptAt(
      now = now,
      failureCount = target.attemptCount + 1,
      failure = failure,
      retryConfig = applicationConfig.spotify.client.retry
    )

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        accessTokenWriter.markRefreshFailed(
          authorizationId = target.authorizationId,
          queueId = target.queueId,
          queueStatus = QueueJobStatus.Scheduled,
          attemptCount = target.attemptCount + 1,
          nextAttemptAt = Some(nextAttemptAt),
          lastFailedAt = now,
          lastErrorType = failure.reason.dbValue,
          lastAttemptedAt = now,
          lockToken = "",
          lockedUntil = Option.empty,
          updatedAt = now,
          updatedUser = AuditUser.System,
          lockVersion = target.queueLockVersion + 1L,
          expectedQueueStatus = QueueJobStatus.Scheduled,
          expectedQueueLockVersion = target.queueLockVersion
        )
      }
    }(using databaseExecutor).flatMap {
      case true =>
        Future.failed(ProviderException.TemporaryFailure(target.userId, failure.reason.dbValue, nextAttemptAt, cause.orNull))
      case false =>
        Future.failed(ProviderException.ConcurrentUpdate(target.userId))
    }
  }
}
