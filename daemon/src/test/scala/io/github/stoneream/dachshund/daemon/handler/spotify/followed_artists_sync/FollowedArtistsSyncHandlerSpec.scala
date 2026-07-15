package io.github.stoneream.dachshund.daemon.handler.spotify.followed_artists_sync

import com.google.inject.AbstractModule
import io.github.stoneream.dachshund.daemon.config.FollowedArtistsSyncJobConfig
import io.github.stoneream.dachshund.daemon.test.DaemonHandlerDatabaseSpecSupport
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{FollowedArtistSyncQueueWriter, SpotifyAuthorizationRefreshQueueWriter, SpotifyAuthorizationWriter, SpotifyUserWriter, UserFollowedArtistsWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.{EncryptedSpotifyToken, SpotifyTokenEncryptionAad, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.model.QueueJobStatus
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientException}
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.FollowedArtistsSyncUseCaseException
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import scala.concurrent.Future
import scala.concurrent.duration.*

class FollowedArtistsSyncHandlerSpec extends AnyFeatureSpec with DaemonHandlerDatabaseSpecSupport with IdiomaticMockito {
  import FollowedArtistsSyncHandlerFixture.*

  private given LoggingContext = LoggingContext("followed-artists-sync-handler-spec")

  private lazy val tokenEncryptor = new SpotifyTokenEncryptor(testApplicationConfig)
  private val userWriter = new SpotifyUserWriter
  private val authorizationWriter = new SpotifyAuthorizationWriter
  private val refreshQueueWriter = new SpotifyAuthorizationRefreshQueueWriter
  private val followedQueueWriter = new FollowedArtistSyncQueueWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter

  Feature("Followed artists sync job handler") {
    Scenario("claim した queue の全ページを保存して最終ページで queue を完了する") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(AllPagesUserRow)
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        followedQueueWriter.write(allPagesQueueRow(userId))
        userId
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.getFollowedArtists("current-access-token", Some("current-cursor"), 50)(using *[LoggingContext]) returns
        Future.successful(AllPagesFirstPage)
      spotifyClient.getFollowedArtists("current-access-token", Some("next-cursor"), 50)(using *[LoggingContext]) returns
        Future.successful(AllPagesSecondPage)
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        followedArtistRows() == Seq(
          FollowedArtistRow(userId, "artist-all-pages-1", Some(syncDateMarker), Some(fixedNow), 0L, 0L),
          FollowedArtistRow(userId, "artist-all-pages-2", Some(syncDateMarker), Some(fixedNow), 0L, 0L)
        )
      )
      assert(
        followedQueueRows() == Seq(
          FollowedQueueRow(
            userId = userId,
            status = QueueJobStatus.Succeeded.dbValue,
            afterCursor = None,
            nextAttemptAt = None,
            lastAttemptedAt = Some(fixedNow),
            completedAt = Some(fixedNow),
            attemptCount = 0,
            lastFailedAt = None,
            lastErrorType = "",
            lockToken = "",
            lockedUntil = None,
            lockVersion = 3L
          )
        )
      )
    }

    Scenario("最終ページでは同期日に見えなかった行を削除して queue を完了にする") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(DeletionUserRow)
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        followedQueueWriter.write(deletionQueueRow(userId))
        followedArtistWriter.write(deletionOldArtistFirstRow(userId))
        followedArtistWriter.write(deletionOldArtistSecondRow(userId))
        followedArtistWriter.write(deletionOldArtistThirdRow(userId))
        userId
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.getFollowedArtists("current-access-token", None, 50)(using *[LoggingContext]) returns
        Future.successful(DeletionPage)
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        followedArtistRows() == Seq(
          FollowedArtistRow(userId, "artist-deleted-1", Some(oldSeenAt), Some(fixedNow), 1L, 2L),
          FollowedArtistRow(userId, "artist-deleted-2", Some(oldSeenAt), Some(fixedNow), 1L, 3L),
          FollowedArtistRow(userId, "artist-deleted-3", Some(oldSeenAt), Some(fixedNow), 1L, 4L),
          FollowedArtistRow(userId, "artist-kept", Some(syncDateMarker), Some(fixedNow), 0L, 0L)
        )
      )
      assert(followedQueueRows().map(row => (row.userId, row.status, row.completedAt)) == Seq((userId, QueueJobStatus.Succeeded.dbValue, Some(fixedNow))))
    }

    Scenario("401 の場合は token を強制 refresh して同じページを 1 回だけ再取得する") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(UnauthorizedUserRow)
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        followedQueueWriter.write(unauthorizedQueueRow(userId))
        userId
      }
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.refreshAccessToken("current-refresh-token", "spotify-client-id", "spotify-client-secret")(using *[LoggingContext]) returns
        Future.successful(TokenResponse("refreshed-access-token", "Bearer", 3600, Some("refreshed-refresh-token"), Some("user-follow-read")))
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.getFollowedArtists("current-access-token", None, 50)(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.Unauthorized(new RuntimeException("unauthorized")))
      spotifyClient.getFollowedArtists("refreshed-access-token", None, 50)(using *[LoggingContext]) returns
        Future.successful(UnauthorizedRetryPage)
      val handler = createHandler(spotifyOAuthClient, spotifyClient)

      unsafeRun(handler.handle())

      assert(followedArtistRows().map(row => (row.userId, row.spotifyArtistCode, row.deleted)) == Seq((userId, "artist-unauthorized-retry", 0L)))
      assert(
        authorizationRows().map(row => (row.userId, row.scopeText, row.accessTokenExpiresAt, row.lastRefreshedAt, row.lockVersion)) ==
          Seq((userId, "user-follow-read", fixedNow.plus(3600.seconds), Some(fixedNow), 1L))
      )
      assert(
        authorizationRefreshQueueRows().map(row => (row.status, row.nextAttemptAt, row.completedAt, row.lastErrorType, row.lockVersion)) ==
          Seq((QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(3300.seconds)), Some(fixedNow), "", 1L))
      )
      assert(followedQueueRows().map(row => (row.userId, row.status, row.completedAt)) == Seq((userId, QueueJobStatus.Succeeded.dbValue, Some(fixedNow))))
    }

    Scenario("scope 不足は blocked として queue に保存する") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(ForbiddenUserRow)
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        followedQueueWriter.write(forbiddenQueueRow(userId))
        userId
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.getFollowedArtists("current-access-token", None, 50)(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.Forbidden(new RuntimeException("forbidden")))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(followedArtistRows().isEmpty)
      assert(
        followedQueueRows().map(row => (row.userId, row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.lockToken)) ==
          Seq((userId, QueueJobStatus.Blocked.dbValue, None, Some(fixedNow), "insufficient_scope", ""))
      )
    }

    Scenario("rate limit は一時失敗として retry 時刻を保存する") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(RateLimitedUserRow)
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        followedQueueWriter.write(rateLimitedQueueRow(userId))
        userId
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.getFollowedArtists("current-access-token", None, 50)(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.RateLimited(Some(10.seconds), new RuntimeException("rate limited")))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(
        followedQueueRows().map(row => (row.userId, row.status, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.attemptCount)) ==
          Seq((userId, QueueJobStatus.Scheduled.dbValue, Some(fixedNow.plus(10.seconds)), Some(fixedNow), "rate_limited", 3))
      )
    }

    Scenario("途中ページの rate limit は保存済み cursor から retry できるようにする") {
      val userId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(CursorRateLimitedUserRow)
        writeAuthorization(userId, "current-access-token", "current-refresh-token")
        followedQueueWriter.write(cursorRateLimitedQueueRow(userId))
        userId
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.getFollowedArtists("current-access-token", None, 50)(using *[LoggingContext]) returns
        Future.successful(CursorRateLimitedFirstPage)
      spotifyClient.getFollowedArtists("current-access-token", Some("next-cursor"), 50)(using *[LoggingContext]) returns
        Future.failed(SpotifyClientException.RateLimited(Some(10.seconds), new RuntimeException("rate limited")))
      val handler = createHandler(mock[SpotifyOAuthClient], spotifyClient)

      unsafeRun(handler.handle())

      assert(followedArtistRows().map(row => (row.userId, row.spotifyArtistCode, row.deleted)) == Seq((userId, "artist-cursor-first", 0L)))
      assert(
        followedQueueRows().map(row =>
          (row.userId, row.status, row.afterCursor, row.nextAttemptAt, row.lastFailedAt, row.lastErrorType, row.attemptCount, row.lockVersion)
        ) ==
          Seq((userId, QueueJobStatus.Scheduled.dbValue, Some("next-cursor"), Some(fixedNow.plus(10.seconds)), Some(fixedNow), "rate_limited", 3, 3L))
      )
    }

    Scenario("ページ保存が想定外の失敗になった場合は claim 済み queue を release して失敗する") {
      val firstUserId = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val firstUserId = userWriter.write(UnexpectedFailureFirstUserRow)
        val secondUserId = userWriter.write(UnexpectedFailureSecondUserRow)
        writeAuthorization(firstUserId, "current-access-token", "current-refresh-token")
        followedQueueWriter.write(unexpectedFailureFirstQueueRow(firstUserId))
        followedQueueWriter.write(unexpectedFailureSecondQueueRow(secondUserId))
        firstUserId
      }
      val spotifyClient = mock[SpotifyClient]
      spotifyClient.getFollowedArtists("current-access-token", None, 50)(using *[LoggingContext]) returns
        Future.successful(UnexpectedFailurePage)
      val handler = createHandler(
        mock[SpotifyOAuthClient],
        spotifyClient,
        Some(testDaemonConfig.jobs.followedArtistsSync.copy(batchSize = 2))
      )

      val exception = intercept[FollowedArtistsSyncUseCaseException.Unexpected] {
        unsafeRun(handler.handle())
      }

      assert(exception.getCause.isInstanceOf[Throwable])
      assert(followedArtistRows().isEmpty)
      assert(
        followedQueueRows().map(row => (row.status, row.nextAttemptAt, row.lastFailedAt, row.lockToken, row.lockedUntil, row.lockVersion)) ==
          Seq(
            (QueueJobStatus.Scheduled.dbValue, Some(fixedNow), None, "", None, 2L),
            (QueueJobStatus.Scheduled.dbValue, Some(fixedNow), None, "", None, 2L)
          )
      )
      assert(followedQueueRows().exists(_.userId == firstUserId))
    }
  }

  private def createHandler(
      spotifyOAuthClient: SpotifyOAuthClient,
      spotifyClient: SpotifyClient,
      config: Option[FollowedArtistsSyncJobConfig] = None
  ): FollowedArtistsSyncHandler = {
    val module = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[SpotifyOAuthClient]).toInstance(spotifyOAuthClient)
        bind(classOf[SpotifyClient]).toInstance(spotifyClient)
        config.foreach(value => bind(classOf[FollowedArtistsSyncJobConfig]).toInstance(value))
      }
    }
    createInjector(fixedNow, module).getInstance(classOf[FollowedArtistsSyncHandler])
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
    val authorizationId = authorizationIdByUserId(userId)
    refreshQueueWriter.write(authorizationRefreshQueueRow(authorizationId))
    authorizationId
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

  private def followedArtistRows(): Seq[FollowedArtistRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          user_id,
          spotify_artist_code,
          last_seen_at,
          last_synced_at,
          deleted,
          lock_version
        from user_followed_artist
        order by user_id asc, spotify_artist_code asc
      """
        .map { rs =>
          FollowedArtistRow(
            userId = rs.long("user_id"),
            spotifyArtistCode = rs.string("spotify_artist_code"),
            lastSeenAt = rs.localDateTimeOpt("last_seen_at").map(BusinessDateTime.fromLocalDateTime),
            lastSyncedAt = rs.localDateTimeOpt("last_synced_at").map(BusinessDateTime.fromLocalDateTime),
            deleted = rs.long("deleted"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private def followedQueueRows(): Seq[FollowedQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          user_id,
          status,
          after_cursor,
          next_attempt_at,
          last_attempted_at,
          completed_at,
          attempt_count,
          last_failed_at,
          last_error_type,
          lock_token,
          locked_until,
          lock_version
        from followed_artist_sync_queue
        order by user_id asc
      """
        .map { rs =>
          FollowedQueueRow(
            userId = rs.long("user_id"),
            status = rs.string("status"),
            afterCursor = rs.stringOpt("after_cursor"),
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

  private def authorizationRefreshQueueRows(): Seq[AuthorizationRefreshQueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          status,
          next_attempt_at,
          completed_at,
          last_error_type,
          lock_version
        from user_spotify_authorization_refresh_queue
        order by id asc
      """
        .map { rs =>
          AuthorizationRefreshQueueRow(
            status = rs.string("status"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
            completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
            lastErrorType = rs.string("last_error_type"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private final case class FollowedArtistRow(
      userId: Long,
      spotifyArtistCode: String,
      lastSeenAt: Option[BusinessDateTime],
      lastSyncedAt: Option[BusinessDateTime],
      deleted: Long,
      lockVersion: Long
  )

  private final case class FollowedQueueRow(
      userId: Long,
      status: String,
      afterCursor: Option[String],
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

  private final case class AuthorizationRow(
      userId: Long,
      scopeText: String,
      accessTokenExpiresAt: BusinessDateTime,
      lastRefreshedAt: Option[BusinessDateTime],
      lockVersion: Long
  )

  private final case class AuthorizationRefreshQueueRow(
      status: String,
      nextAttemptAt: Option[BusinessDateTime],
      completedAt: Option[BusinessDateTime],
      lastErrorType: String,
      lockVersion: Long
  )
}
