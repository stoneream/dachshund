package io.github.stoneream.dachshund.auth

import io.github.stoneream.dachshund.auth.UserSessionContext.{NormalUser, NotLoggedIn}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSessionTokenDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{UserSessionTokenSource, UserSource}
import io.github.stoneream.dachshund.infra.db.reader.auth.UserSessionTokenReader
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyUserWriter, UserSessionTokenWriter}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}
import io.github.stoneream.dachshund.test.lib.db.DatabaseSupport
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec
import io.github.stoneream.dachshund.infra.db.generated.UserSessionTokenDbRow

class UserSessionContextResolverSpec extends AnyFeatureSpec with ScalaFutures with DatabaseSupport with IdiomaticMockito {
  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")
  private val dateTimeService = mock[DateTimeService]
  dateTimeService.now() returns fixedNow

  private val sessionTokenService = new SessionTokenService(testApplicationConfig, dateTimeService)
  private val resolver = new UserSessionContextResolver(
    databaseTransaction = databaseTransaction,
    userSessionTokenReader = new UserSessionTokenReader,
    sessionTokenService = sessionTokenService,
    databaseExecutor = databaseExecutor
  )
  private val userWriter = new SpotifyUserWriter
  private val userSessionTokenWriter = new UserSessionTokenWriter

  Feature("User session context resolver") {
    Scenario("session token がない場合は未ログインにする") {
      val result = resolver.resolve(None, fixedNow).futureValue

      assert(result == NotLoggedIn)
    }

    Scenario("不正な session token は未ログインにする") {
      val result = resolver.resolve(Some("invalid-token"), fixedNow).futureValue

      assert(result == NotLoggedIn)
    }

    Scenario("有効な session token の hash に紐づくユーザーを返す") {
      val issued = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.UserRow)

        val issuedSessionToken = sessionTokenService.issue(userId)
        userSessionTokenWriter.write(Rows.sessionTokenRow(userId, issuedSessionToken))

        issuedSessionToken
      }

      val result = resolver.resolve(Some(issued.value), fixedNow).futureValue

      assert(result == NormalUser(userId = issued.userId, userName = "user-name", displayName = "display name"))
    }
  }

  private object Rows {
    val UserRow = UserSource(
      userName = "user-name",
      displayName = "display name",
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

    def sessionTokenRow(
        userId: Long,
        issuedSessionToken: SessionTokenService.IssuedSessionToken
    ): UserSessionTokenDbRow =
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
}
