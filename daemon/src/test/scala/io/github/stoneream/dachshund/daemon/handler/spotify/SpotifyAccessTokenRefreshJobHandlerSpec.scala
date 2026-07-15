package io.github.stoneream.dachshund.daemon.handler.spotify

import com.google.inject.AbstractModule
import io.github.stoneream.dachshund.daemon.config.SpotifyAccessTokenRefreshJobConfig
import io.github.stoneream.dachshund.daemon.handler.spotify.SpotifyAccessTokenRefreshJobHandlerFixture.*
import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyAuthorizationRefreshQueueWriter, SpotifyAuthorizationWriter, SpotifyUserWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.{EncryptedSpotifyToken, SpotifyTokenEncryptionAad, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClientException.SpotifyApiClientError
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.SpotifyAccessTokenRefreshUseCaseException
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import scala.concurrent.Future
import scala.concurrent.duration.*

class SpotifyAccessTokenRefreshJobHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport with IdiomaticMockito {
  private given LoggingContext = LoggingContext("spotify-access-token-refresh-job-handler-spec")

  private lazy val tokenEncryptor = new SpotifyTokenEncryptor(testApplicationConfig)
  private val userWriter = new SpotifyUserWriter
  private val authorizationWriter = new SpotifyAuthorizationWriter
  private val refreshQueueWriter = new SpotifyAuthorizationRefreshQueueWriter

  Feature("Spotify access token refresh job handler") {
    Scenario("更新対象がない場合は DB を更新せず正常完了する") {
      val handler = createHandler(mock[SpotifyOAuthClient])

      unsafeRun(handler.handle())

      assert(authorizationRows().isEmpty)
      assert(refreshQueueRows().isEmpty)
    }

    Scenario("claim 前に stale な PROCESSING queue を復旧してから refresh 対象を処理する") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(StaleProcessingUserRow)
        val authorizationId = writeAuthorization(userId, "stale-current-access-token", "stale-current-refresh-token")
        refreshQueueWriter.write(staleProcessingRefreshQueueRow(authorizationId))
        userId
      }
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.refreshAccessToken("stale-current-refresh-token", "spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
        Future.successful(TokenResponse("stale-new-access-token", "Bearer", 3600, Some("stale-new-refresh-token"), Some("user-follow-read")))
      val handler = createHandler(spotifyOAuthClient)

      unsafeRun(handler.handle())

      assert(
        authorizationRows().map(row => (row.userId, row.scopeText, row.accessTokenExpiresAt, row.lastRefreshedAt, row.lockVersion)) ==
          Seq((userId, "user-follow-read", fixedNow.plus(3600.seconds), Some(fixedNow), 1L))
      )
      assert(
        refreshQueueRows().map(row =>
          (
            row.status,
            row.nextAttemptAt,
            row.lastAttemptedAt,
            row.completedAt,
            row.attemptCount,
            row.lastErrorType,
            row.lockToken,
            row.lockedUntil,
            row.lockVersion
          )
        ) ==
          Seq((QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(3300.seconds)), Some(fixedNow), Some(fixedNow), 0, "", "", None, 3L))
      )
    }

    Scenario("refresh が成功した target ごとに authorization と queue を更新する") {
      val (firstUserId, secondUserId) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val firstUserId = userWriter.write(SuccessFirstUserRow)
        val secondUserId = userWriter.write(SuccessSecondUserRow)
        val firstAuthorizationId = writeAuthorization(firstUserId, "first-current-access-token", "first-current-refresh-token")
        val secondAuthorizationId = writeAuthorization(secondUserId, "second-current-access-token", "second-current-refresh-token")
        refreshQueueWriter.write(successFirstRefreshQueueRow(firstAuthorizationId))
        refreshQueueWriter.write(successSecondRefreshQueueRow(secondAuthorizationId))
        (firstUserId, secondUserId)
      }
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.refreshAccessToken("first-current-refresh-token", "spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
        Future.successful(TokenResponse("first-new-access-token", "Bearer", 3600, Some("first-new-refresh-token"), Some("user-follow-read")))
      spotifyOAuthClient.refreshAccessToken("second-current-refresh-token", "spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
        Future.successful(TokenResponse("second-new-access-token", "Bearer", 3600, Some("second-new-refresh-token"), Some("user-follow-read")))
      val handler = createHandler(
        spotifyOAuthClient,
        Some(testDaemonConfig.jobs.spotifyAccessTokenRefresh.copy(batchSize = 2))
      )

      unsafeRun(handler.handle())

      assert(
        authorizationRows().map(row => (row.userId, row.lastRefreshedAt, row.lockVersion)) ==
          Seq((firstUserId, Some(fixedNow), 1L), (secondUserId, Some(fixedNow), 1L))
      )
      assert(
        refreshQueueRows().map(row => (row.status, row.nextAttemptAt, row.completedAt, row.attemptCount, row.lastErrorType, row.lockToken, row.lockVersion)) ==
          Seq(
            (QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(3300.seconds)), Some(fixedNow), 0, "", "", 2L),
            (QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(3300.seconds)), Some(fixedNow), 0, "", "", 2L)
          )
      )
    }

    Scenario("refresh token 復号失敗は再認可が必要な失敗として queue に保存する") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(DecryptFailureUserRow)
        val authorizationId = writeAuthorizationWithWrongRefreshTokenAad(userId, "decrypt-current-access-token", "decrypt-current-refresh-token")
        refreshQueueWriter.write(decryptFailureRefreshQueueRow(authorizationId))
        userId
      }
      val handler = createHandler(mock[SpotifyOAuthClient])

      unsafeRun(handler.handle())

      assert(authorizationRows().map(row => (row.userId, row.lastRefreshedAt, row.lockVersion)) == Seq((userId, None, 0L)))
      assert(
        refreshQueueRows().map(row =>
          (row.status, row.nextAttemptAt, row.lastAttemptedAt, row.lastFailedAt, row.lastErrorType, row.lockToken, row.lockVersion)
        ) ==
          Seq((QueueJobStatus.Blocked.dbValue, None, Some(fixedNow), Some(fixedNow), "token_decrypt_failed", "", 2L))
      )
    }

    Scenario("daemon 全体を abort する失敗では claim 済み target を release する") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(InvalidClientUserRow)
        val authorizationId = writeAuthorization(userId, "invalid-client-current-access-token", "invalid-client-current-refresh-token")
        refreshQueueWriter.write(invalidClientRefreshQueueRow(authorizationId))
      }
      val invalidClientException = SpotifyOAuthClientException.TokenRefreshFailed(
        SpotifyApiClientError(
          endpoint = "https://accounts.spotify.com/api/token",
          statusCode = 400,
          errorCode = Some("invalid_client"),
          errorDescription = Some("client authentication failed")
        )
      )
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.refreshAccessToken("invalid-client-current-refresh-token", "spotify-client-id", "spotify-client-secret")(using
        *[LoggingContext]
      ) returns
        Future.failed(invalidClientException)
      val handler = createHandler(spotifyOAuthClient)

      val exception = intercept[SpotifyAccessTokenRefreshUseCaseException.InvalidClientCredentials] {
        unsafeRun(handler.handle())
      }

      assert(exception.causeException == invalidClientException)
      assert(
        refreshQueueRows().map(row =>
          (
            row.status,
            row.nextAttemptAt,
            row.lastAttemptedAt,
            row.completedAt,
            row.attemptCount,
            row.lastFailedAt,
            row.lastErrorType,
            row.lockToken,
            row.lockedUntil,
            row.lockVersion
          )
        ) ==
          Seq((QueueJobStatus.Scheduled.dbValue, Some(fixedNow), Some(fixedNow), None, 1, None, "", "", None, 2L))
      )
    }
  }

  private def createHandler(
      spotifyOAuthClient: SpotifyOAuthClient,
      config: Option[SpotifyAccessTokenRefreshJobConfig] = None
  ): SpotifyAccessTokenRefreshJobHandler = {
    val module = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[SpotifyOAuthClient]).toInstance(spotifyOAuthClient)
        config.foreach(value => bind(classOf[SpotifyAccessTokenRefreshJobConfig]).toInstance(value))
      }
    }
    createInjector(fixedNow, module).getInstance(classOf[SpotifyAccessTokenRefreshJobHandler])
  }

  private def writeAuthorization(
      userId: Long,
      accessToken: String,
      refreshToken: String
  )(using DBSession): Long = {
    authorizationWriter.write(
      authorizationRow(
        userId = userId,
        encryptedAccessToken = encryptedAccessToken(userId, accessToken),
        encryptedRefreshToken = encryptedRefreshToken(userId, refreshToken)
      )
    )
    authorizationIdByUserId(userId)
  }

  private def writeAuthorizationWithWrongRefreshTokenAad(
      userId: Long,
      accessToken: String,
      refreshToken: String
  )(using DBSession): Long = {
    authorizationWriter.write(
      authorizationRow(
        userId = userId,
        encryptedAccessToken = encryptedAccessToken(userId, accessToken),
        encryptedRefreshToken = encryptedRefreshToken(userId + 999L, refreshToken)
      )
    )
    authorizationIdByUserId(userId)
  }

  private def encryptedAccessToken(userId: Long, token: String): EncryptedSpotifyToken =
    tokenEncryptor.encrypt(
      token,
      Some(SpotifyTokenEncryptionAad.accessToken(userId, testApplicationConfig.spotify.token.encryptionKeyVersion))
    )

  private def encryptedRefreshToken(userId: Long, token: String): EncryptedSpotifyToken =
    tokenEncryptor.encrypt(
      token,
      Some(SpotifyTokenEncryptionAad.refreshToken(userId, testApplicationConfig.spotify.token.encryptionKeyVersion))
    )

  private def authorizationIdByUserId(userId: Long)(using DBSession): Long =
    sql"select id from user_spotify_authorization where user_id = {userId}"
      .bindByName("userId" -> userId)
      .map(_.long("id"))
      .single
      .apply()
      .get

  private def authorizationRows(): Seq[AuthorizationRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          user_id,
          scope_text,
          access_token_expires_at,
          last_refreshed_at,
          lock_version
        from user_spotify_authorization
        order by user_id asc
      """
        .map { rs =>
          AuthorizationRow(
            userId = rs.long("user_id"),
            scopeText = rs.string("scope_text"),
            accessTokenExpiresAt = BusinessDateTime.fromLocalDateTime(rs.localDateTime("access_token_expires_at")),
            lastRefreshedAt = rs.localDateTimeOpt("last_refreshed_at").map(BusinessDateTime.fromLocalDateTime),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private def refreshQueueRows(): Seq[RefreshQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          status,
          next_attempt_at,
          last_attempted_at,
          completed_at,
          attempt_count,
          last_failed_at,
          last_error_type,
          lock_token,
          locked_until,
          lock_version
        from user_spotify_authorization_refresh_queue
        order by id asc
      """
        .map { rs =>
          RefreshQueueRow(
            status = rs.string("status"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
            lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
            completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
            attemptCount = rs.int("attempt_count"),
            lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
            lastErrorType = rs.string("last_error_type"),
            lockToken = rs.string("lock_token"),
            lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private final case class AuthorizationRow(
      userId: Long,
      scopeText: String,
      accessTokenExpiresAt: BusinessDateTime,
      lastRefreshedAt: Option[BusinessDateTime],
      lockVersion: Long
  )

  private final case class RefreshQueueRow(
      status: String,
      nextAttemptAt: Option[BusinessDateTime],
      lastAttemptedAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      attemptCount: Int,
      lastFailedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[BusinessDateTime],
      lockVersion: Long
  )
}
