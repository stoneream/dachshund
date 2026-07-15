package io.github.stoneream.dachshund.service.spotify.auth.access_token

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.reader.access_token.SpotifyAccessTokenReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.SpotifyAccessTokenWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.{EncryptedSpotifyToken, SpotifyTokenEncryptionAad, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException.SpotifyApiClientError
import io.github.stoneream.dachshund.service.spotify.auth.access_token.SpotifyAuthorizationCodeAccessTokenProviderException as ProviderException
import io.github.stoneream.dachshund.service.spotify.auth.access_token.model.SpotifyAccessTokenResolveTarget
import io.github.stoneream.dachshund.service.spotify.auth.access_token.step.*
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.DBSession

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.*

class SpotifyAuthorizationCodeAccessTokenProviderSpec extends AnyFeatureSpec with ScalaFutures {
  private given LoggingContext = LoggingContext("spotify-authorization-code-access-token-provider-spec")

  Feature("Spotify authorization code access token provider") {
    Scenario("有効期限内の access token は refresh せず復号して返す") {
      val fixture = buildFixture(target = Some(resolveTarget(accessToken = "current-access-token")))

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).futureValue

      assert(result.accessToken == "current-access-token")
      assert(result.tokenType == "Bearer")
      assert(result.scopeText == "user-follow-read")
      assert(result.expiresAt.toLocalDateTime == fixedNow.plus(3600.seconds).toLocalDateTime)
      assert(fixture.oauthClient.refreshRequests.isEmpty)
      assert(fixture.writer.succeeded.isEmpty)
    }

    Scenario("有効期限内の access token を使う場合は refresh token を復号しない") {
      val fixture = buildFixture(
        target = Some(resolveTarget(accessToken = "current-access-token", encryptedRefreshToken = invalidEncryptedToken))
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).futureValue

      assert(result.accessToken == "current-access-token")
      assert(fixture.oauthClient.refreshRequests.isEmpty)
      assert(fixture.writer.reauthorizationRequired.isEmpty)
    }

    Scenario("期限前更新範囲内の access token は refresh して保存後に返す") {
      val target = resolveTarget(accessTokenExpiresAt = fixedNow.plus(100.seconds))
      val fixture = buildFixture(target = Some(target))
      fixture.oauthClient.refreshResult =
        Future.successful(TokenResponse("new-access-token", "Bearer", 3600L, Some("new-refresh-token"), Some("user-follow-read")))

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).futureValue

      assert(result.accessToken == "new-access-token")
      assert(result.expiresAt.toLocalDateTime == fixedNow.plus(3600.seconds).toLocalDateTime)
      assert(fixture.oauthClient.refreshRequests == Vector("current-refresh-token"))
      assert(fixture.writer.succeeded.size == 1)
      assert(fixture.writer.succeeded.head.nextRefreshAttemptAt.toLocalDateTime == fixedNow.plus(3300.seconds).toLocalDateTime)
    }

    Scenario("refresh する場合は access token を復号しない") {
      val target = resolveTarget(
        encryptedAccessToken = invalidEncryptedToken,
        accessTokenExpiresAt = fixedNow.plus(100.seconds)
      )
      val fixture = buildFixture(target = Some(target))
      fixture.oauthClient.refreshResult =
        Future.successful(TokenResponse("new-access-token", "Bearer", 3600L, Some("new-refresh-token"), Some("user-follow-read")))

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).futureValue

      assert(result.accessToken == "new-access-token")
      assert(fixture.oauthClient.refreshRequests == Vector("current-refresh-token"))
      assert(fixture.writer.reauthorizationRequired.isEmpty)
    }

    Scenario("forceRefresh が true の場合は有効期限内でも refresh して返す") {
      val fixture = buildFixture(target = Some(resolveTarget()))
      fixture.oauthClient.refreshResult = Future.successful(TokenResponse("forced-access-token", "Bearer", 3600L, None, None))

      val result = fixture.provider
        .resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow, forceRefresh = true))
        .futureValue

      assert(result.accessToken == "forced-access-token")
      assert(result.scopeText == "user-follow-read")
      assert(fixture.oauthClient.refreshRequests == Vector("current-refresh-token"))
      assert(fixture.writer.succeeded.size == 1)
    }

    Scenario("refresh 処理中でも access token が期限内なら現在の token を返す") {
      val fixture = buildFixture(
        target = Some(
          resolveTarget(
            accessToken = "processing-current-access-token",
            accessTokenExpiresAt = fixedNow.plus(100.seconds),
            queueStatus = QueueJobStatus.Processing.dbValue
          )
        )
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).futureValue

      assert(result.accessToken == "processing-current-access-token")
      assert(result.expiresAt.toLocalDateTime == fixedNow.plus(100.seconds).toLocalDateTime)
      assert(fixture.oauthClient.refreshRequests.isEmpty)
      assert(fixture.writer.succeeded.isEmpty)
      assert(fixture.writer.reauthorizationRequired.isEmpty)
      assert(fixture.writer.temporaryFailures.isEmpty)
    }

    Scenario("refresh 処理中で access token が期限切れの場合は並行更新失敗を返す") {
      val fixture = buildFixture(
        target = Some(
          resolveTarget(
            accessTokenExpiresAt = fixedNow.minus(1.second),
            queueStatus = QueueJobStatus.Processing.dbValue
          )
        )
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result == ProviderException.ConcurrentUpdate(3L))
      assert(fixture.oauthClient.refreshRequests.isEmpty)
      assert(fixture.writer.succeeded.isEmpty)
    }

    Scenario("refresh 処理中で forceRefresh の場合は並行更新失敗を返す") {
      val fixture = buildFixture(
        target = Some(
          resolveTarget(
            queueStatus = QueueJobStatus.Processing.dbValue
          )
        )
      )

      val result = fixture.provider
        .resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow, forceRefresh = true))
        .failed
        .futureValue

      assert(result == ProviderException.ConcurrentUpdate(3L))
      assert(fixture.oauthClient.refreshRequests.isEmpty)
      assert(fixture.writer.succeeded.isEmpty)
    }

    Scenario("認可情報がない場合は AuthorizationNotFound として失敗する") {
      val fixture = buildFixture(target = None)

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result == ProviderException.AuthorizationNotFound(3L))
    }

    Scenario("既に再認可要求状態の場合は保存済みの失敗理由を返す") {
      val fixture = buildFixture(
        target = Some(
          resolveTarget(
            queueStatus = QueueJobStatus.Blocked.dbValue,
            lastErrorType = Some("invalid_grant")
          )
        )
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result == ProviderException.ReauthorizationRequired(3L, "invalid_grant"))
      assert(fixture.oauthClient.refreshRequests.isEmpty)
      assert(fixture.writer.reauthorizationRequired.isEmpty)
      assert(fixture.writer.temporaryFailures.isEmpty)
    }

    Scenario("access token 復号失敗は再認可要求として保存する") {
      val fixture = buildFixture(target = Some(resolveTarget(encryptedAccessToken = invalidEncryptedToken)))

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result == ProviderException.ReauthorizationRequired(3L, "token_decrypt_failed"))
      assert(fixture.writer.reauthorizationRequired == Vector((1L, 2L, "token_decrypt_failed")))
    }

    Scenario("refresh token 復号失敗は再認可要求として保存する") {
      val target = resolveTarget(
        accessTokenExpiresAt = fixedNow.plus(100.seconds),
        encryptedRefreshToken = invalidEncryptedToken
      )
      val fixture = buildFixture(target = Some(target))

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result == ProviderException.ReauthorizationRequired(3L, "token_decrypt_failed"))
      assert(fixture.writer.reauthorizationRequired == Vector((1L, 2L, "token_decrypt_failed")))
      assert(fixture.oauthClient.refreshRequests.isEmpty)
    }

    Scenario("invalid_grant は再認可要求として保存する") {
      val fixture = buildFixture(target = Some(resolveTarget(accessTokenExpiresAt = fixedNow.plus(100.seconds))))
      fixture.oauthClient.refreshResult = Future.failed(
        SpotifyOAuthClientException.TokenRefreshFailed(
          SpotifyApiClientError(
            endpoint = "accounts-token-refresh",
            statusCode = 400,
            errorCode = Some("invalid_grant"),
            errorDescription = Some("revoked")
          )
        )
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result.isInstanceOf[ProviderException.ReauthorizationRequired])
      assert(fixture.writer.reauthorizationRequired == Vector((1L, 2L, "invalid_grant")))
    }

    Scenario("refresh 成功レスポンスの scope が不足している場合は再認可要求として保存する") {
      val fixture = buildFixture(
        target = Some(resolveTarget(scopeText = "user-follow-read user-read-private", accessTokenExpiresAt = fixedNow.plus(100.seconds)))
      )
      fixture.oauthClient.refreshResult = Future.successful(TokenResponse("new-access-token", "Bearer", 3600L, None, Some("user-follow-read")))

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result == ProviderException.ReauthorizationRequired(3L, "insufficient_scope"))
      assert(fixture.writer.reauthorizationRequired == Vector((1L, 2L, "insufficient_scope")))
      assert(fixture.writer.succeeded.isEmpty)
    }

    Scenario("rate limit は一時失敗として retry 時刻を保存する") {
      val fixture = buildFixture(target = Some(resolveTarget(accessTokenExpiresAt = fixedNow.plus(100.seconds))))
      fixture.oauthClient.refreshResult = Future.failed(
        SpotifyOAuthClientException.TokenRefreshFailed(
          SpotifyApiClientError(
            endpoint = "accounts-token-refresh",
            statusCode = 429,
            errorCode = None,
            errorDescription = None,
            retryAfter = Some(10.seconds)
          )
        )
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result.isInstanceOf[ProviderException.TemporaryFailure])
      val failure = result.asInstanceOf[ProviderException.TemporaryFailure]
      assert(failure.failureType == "rate_limited")
      assert(failure.nextAttemptAt.toLocalDateTime == fixedNow.plus(10.seconds).toLocalDateTime)
      assert(fixture.writer.temporaryFailures == Vector((1L, 2L, "rate_limited", fixedNow.plus(10.seconds).toLocalDateTime)))
    }

    Scenario("retry 時刻前の一時失敗状態では再 refresh せず一時失敗を返す") {
      val fixture = buildFixture(
        target = Some(
          resolveTarget(
            accessTokenExpiresAt = fixedNow.plus(100.seconds),
            nextAttemptAt = Some(fixedNow.plus(10.seconds)),
            lastErrorType = Some("rate_limited")
          )
        )
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result.isInstanceOf[ProviderException.TemporaryFailure])
      val failure = result.asInstanceOf[ProviderException.TemporaryFailure]
      assert(failure.failureType == "rate_limited")
      assert(failure.nextAttemptAt.toLocalDateTime == fixedNow.plus(10.seconds).toLocalDateTime)
      assert(fixture.oauthClient.refreshRequests.isEmpty)
      assert(fixture.writer.temporaryFailures.isEmpty)
    }

    Scenario("invalid_response は一時失敗として保存する") {
      val fixture = buildFixture(target = Some(resolveTarget(accessTokenExpiresAt = fixedNow.plus(100.seconds))))
      fixture.oauthClient.refreshResult = Future.failed(
        SpotifyOAuthClientException.TokenRefreshFailed(
          SpotifyApiClientError(
            endpoint = "accounts-token-refresh",
            statusCode = 400,
            errorCode = Some("invalid_response"),
            errorDescription = None
          )
        )
      )

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result.isInstanceOf[ProviderException.TemporaryFailure])
      val failure = result.asInstanceOf[ProviderException.TemporaryFailure]
      assert(failure.failureType == "invalid_response")
      assert(fixture.writer.temporaryFailures == Vector((1L, 2L, "invalid_response", fixedNow.plus(1.second).toLocalDateTime)))
    }

    Scenario("refresh 保存時の楽観ロック不一致は並行更新失敗として扱う") {
      val fixture = buildFixture(target = Some(resolveTarget(accessTokenExpiresAt = fixedNow.plus(100.seconds))))
      fixture.writer.succeedRefresh = false
      fixture.oauthClient.refreshResult = Future.successful(TokenResponse("new-access-token", "Bearer", 3600L, None, None))

      val result = fixture.provider.resolve(SpotifyAuthorizationCodeAccessTokenResolveInput(userId = 3L, now = fixedNow)).failed.futureValue

      assert(result == ProviderException.ConcurrentUpdate(3L))
    }
  }

  private def buildFixture(
      target: Option[SpotifyAccessTokenResolveTarget]
  ): Fixture = {
    val applicationConfig = TestApplicationConfig()
    val reader = new StubSpotifyAccessTokenReader(target)
    val writer = new StubSpotifyAccessTokenWriter
    val oauthClient = new StubSpotifyOAuthClient
    val databaseTransaction = new StubDatabaseTransaction
    val provider = new SpotifyAuthorizationCodeAccessTokenProviderImpl(
      storedSpotifyAccessTokenStep = new StoredSpotifyAccessTokenStep(
        applicationConfig = applicationConfig,
        databaseTransaction = databaseTransaction,
        accessTokenReader = reader,
        accessTokenWriter = writer,
        spotifyTokenEncryptor = spotifyTokenEncryptor,
        databaseExecutor = DirectExecutor,
        defaultExecutor = DirectExecutor
      ),
      requestSpotifyAccessTokenRefreshStep = new RequestSpotifyAccessTokenRefreshStep(
        applicationConfig = applicationConfig,
        spotifyOAuthClient = oauthClient
      ),
      prepareSpotifyAccessTokenRefreshSuccessStep = new PrepareSpotifyAccessTokenRefreshSuccessStep(
        applicationConfig = applicationConfig,
        spotifyTokenEncryptor = spotifyTokenEncryptor
      ),
      defaultExecutor = DirectExecutor
    )

    Fixture(provider, reader, writer, oauthClient)
  }

  private final case class Fixture(
      provider: SpotifyAuthorizationCodeAccessTokenProvider,
      reader: StubSpotifyAccessTokenReader,
      writer: StubSpotifyAccessTokenWriter,
      oauthClient: StubSpotifyOAuthClient
  )

  private def resolveTarget(
      accessToken: String = "current-access-token",
      refreshToken: String = "current-refresh-token",
      encryptedAccessToken: EncryptedSpotifyToken = null,
      encryptedRefreshToken: EncryptedSpotifyToken = null,
      scopeText: String = "user-follow-read",
      accessTokenExpiresAt: BusinessDateTime = fixedNow.plus(3600.seconds),
      queueStatus: String = QueueJobStatus.Scheduled.dbValue,
      nextAttemptAt: Option[BusinessDateTime] = None,
      lastErrorType: Option[String] = None
  ): SpotifyAccessTokenResolveTarget =
    SpotifyAccessTokenResolveTarget(
      authorizationId = 1L,
      queueId = 2L,
      userId = 3L,
      scopeText = scopeText,
      encryptedAccessToken = Option(encryptedAccessToken).getOrElse(encryptAccessToken(accessToken)),
      encryptedRefreshToken = Option(encryptedRefreshToken).getOrElse(encryptRefreshToken(refreshToken)),
      tokenType = "Bearer",
      accessTokenExpiresAt = accessTokenExpiresAt,
      refreshMarginSeconds = 300,
      attemptCount = 0,
      nextAttemptAt = nextAttemptAt,
      lastErrorType = lastErrorType,
      queueStatus = queueStatus,
      authorizationLockVersion = 4L,
      queueLockVersion = 5L
    )

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  private val spotifyTokenEncryptor = new SpotifyTokenEncryptor(TestApplicationConfig())

  private def encryptAccessToken(token: String): EncryptedSpotifyToken =
    spotifyTokenEncryptor.encrypt(token, Some(SpotifyTokenEncryptionAad.accessToken(3L, "v1")))

  private def encryptRefreshToken(token: String): EncryptedSpotifyToken =
    spotifyTokenEncryptor.encrypt(token, Some(SpotifyTokenEncryptionAad.refreshToken(3L, "v1")))

  private def invalidEncryptedToken: EncryptedSpotifyToken =
    EncryptedSpotifyToken(
      cipherText = Array[Byte](1),
      nonce = Array.fill[Byte](12)(2),
      tag = Array.fill[Byte](16)(3),
      algorithm = "AES-256-GCM",
      keyVersion = "v1"
    )

  private class StubDatabaseTransaction extends DatabaseTransaction(TestApplicationConfig()) {
    override def localTx[A](role: DatabaseRole)(body: DBSession => A): A = {
      val _ = role
      body(null)
    }

    override def readOnly[A](role: DatabaseRole)(body: DBSession => A): A = {
      val _ = role
      body(null)
    }
  }

  private class StubSpotifyAccessTokenReader(target: Option[SpotifyAccessTokenResolveTarget]) extends SpotifyAccessTokenReader {
    var requestedUserIds: Vector[Long] = Vector.empty

    override def findResolveTargetByUserId(userId: Long)(using DBSession): Option[SpotifyAccessTokenResolveTarget] = {
      requestedUserIds = requestedUserIds :+ userId
      target
    }
  }

  private class StubSpotifyAccessTokenWriter extends SpotifyAccessTokenWriter {
    var succeedRefresh: Boolean = true
    var succeedReauthorizationRequired: Boolean = true
    var succeedTemporaryFailure: Boolean = true
    var succeeded: Vector[SucceededWrite] = Vector.empty
    var reauthorizationRequired: Vector[(Long, Long, String)] = Vector.empty
    var temporaryFailures: Vector[(Long, Long, String, LocalDateTime)] = Vector.empty

    override def markRefreshSucceeded(
        authorizationId: Long,
        queueId: Long,
        scopeText: String,
        accessTokenCipher: Array[Byte],
        accessTokenNonce: Array[Byte],
        accessTokenTag: Array[Byte],
        refreshTokenCipher: Array[Byte],
        refreshTokenNonce: Array[Byte],
        refreshTokenTag: Array[Byte],
        encryptionAlgorithm: String,
        encryptionKeyVersion: String,
        tokenType: String,
        accessTokenExpiresAt: BusinessDateTime,
        lastRefreshedAt: BusinessDateTime,
        authorizationUpdatedAt: BusinessDateTime,
        authorizationUpdatedUser: AuditUser,
        authorizationLockVersion: Long,
        queueStatus: QueueJobStatus,
        nextAttemptAt: BusinessDateTime,
        attemptCount: Int,
        lastFailedAt: Option[BusinessDateTime],
        lastErrorType: String,
        lastAttemptedAt: BusinessDateTime,
        completedAt: BusinessDateTime,
        lockToken: String,
        lockedUntil: Option[BusinessDateTime],
        queueUpdatedAt: BusinessDateTime,
        queueUpdatedUser: AuditUser,
        queueDeletedAt: Option[BusinessDateTime],
        queueDeletedUser: AuditUser,
        queueDeleted: Long,
        queueLockVersion: Long,
        expectedAuthorizationLockVersion: Long,
        expectedQueueStatus: QueueJobStatus,
        expectedQueueLockVersion: Long
    )(using DBSession): Boolean = {
      val _ = (
        accessTokenCipher,
        accessTokenNonce,
        accessTokenTag,
        refreshTokenCipher,
        refreshTokenNonce,
        refreshTokenTag,
        encryptionAlgorithm,
        encryptionKeyVersion,
        lastRefreshedAt,
        authorizationUpdatedAt,
        authorizationUpdatedUser,
        authorizationLockVersion,
        queueStatus,
        attemptCount,
        lastFailedAt,
        lastErrorType,
        lastAttemptedAt,
        completedAt,
        lockToken,
        lockedUntil,
        queueUpdatedAt,
        queueUpdatedUser,
        queueDeletedAt,
        queueDeletedUser,
        queueDeleted,
        queueLockVersion,
        expectedAuthorizationLockVersion,
        expectedQueueStatus,
        expectedQueueLockVersion
      )
      succeeded = succeeded :+ SucceededWrite(
        authorizationId = authorizationId,
        queueId = queueId,
        tokenType = tokenType,
        scopeText = scopeText,
        accessTokenExpiresAt = accessTokenExpiresAt,
        nextRefreshAttemptAt = nextAttemptAt
      )
      succeedRefresh
    }

    override def markRefreshFailed(
        authorizationId: Long,
        queueId: Long,
        queueStatus: QueueJobStatus,
        attemptCount: Int,
        nextAttemptAt: Option[BusinessDateTime],
        lastFailedAt: BusinessDateTime,
        lastErrorType: String,
        lastAttemptedAt: BusinessDateTime,
        lockToken: String,
        lockedUntil: Option[BusinessDateTime],
        updatedAt: BusinessDateTime,
        updatedUser: AuditUser,
        lockVersion: Long,
        expectedQueueStatus: QueueJobStatus,
        expectedQueueLockVersion: Long
    )(using DBSession): Boolean =
      queueStatus match {
        case QueueJobStatus.Blocked =>
          val _ = (
            attemptCount,
            nextAttemptAt,
            lastFailedAt,
            lastAttemptedAt,
            lockToken,
            lockedUntil,
            updatedAt,
            updatedUser,
            lockVersion,
            expectedQueueStatus,
            expectedQueueLockVersion
          )
          reauthorizationRequired = reauthorizationRequired :+ ((authorizationId, queueId, lastErrorType))
          succeedReauthorizationRequired
        case QueueJobStatus.Scheduled =>
          val _ = (
            attemptCount,
            lastFailedAt,
            lastAttemptedAt,
            lockToken,
            lockedUntil,
            updatedAt,
            updatedUser,
            lockVersion,
            expectedQueueStatus,
            expectedQueueLockVersion
          )
          val refreshAttemptAt =
            nextAttemptAt.getOrElse(throw IllegalArgumentException("nextAttemptAt is required for temporary failure"))
          temporaryFailures = temporaryFailures :+ ((authorizationId, queueId, lastErrorType, refreshAttemptAt.toLocalDateTime))
          succeedTemporaryFailure
        case _ =>
          throw IllegalArgumentException(s"unexpected queue status: $queueStatus")
      }
  }

  private final case class SucceededWrite(
      authorizationId: Long,
      queueId: Long,
      tokenType: String,
      scopeText: String,
      accessTokenExpiresAt: BusinessDateTime,
      nextRefreshAttemptAt: BusinessDateTime
  )

  private class StubSpotifyOAuthClient extends SpotifyOAuthClient {
    var refreshResult: Future[TokenResponse] =
      Future.failed(new IllegalStateException("refresh result is not configured"))
    var refreshRequests: Vector[String] = Vector.empty

    override def accessTokenRequest(
        code: String,
        redirectUri: String,
        clientId: String,
        clientSecret: String
    )(using LoggingContext): Future[TokenResponse] =
      Future.failed(new IllegalStateException("unused access token request"))

    override def refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String
    )(using LoggingContext): Future[TokenResponse] = {
      val _ = (clientId, clientSecret)
      refreshRequests = refreshRequests :+ refreshToken
      refreshResult
    }

    override def requestClientCredentialsAccessToken(
        clientId: String,
        clientSecret: String
    )(using LoggingContext): Future[TokenResponse] =
      Future.failed(new IllegalStateException("unused client credentials request"))
  }

  private object DirectExecutor extends ExecutionContextExecutor with DefaultExecutor with DatabaseExecutor {
    override def execute(runnable: Runnable): Unit = runnable.run()

    override def reportFailure(cause: Throwable): Unit = throw cause
  }
}
