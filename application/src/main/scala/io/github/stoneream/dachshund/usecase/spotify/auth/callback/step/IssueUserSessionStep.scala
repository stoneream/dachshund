package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.ex.UserSessionTokenDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.UserSessionTokenWriter
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.auth.SessionTokenService.IssuedSessionToken
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor

import scala.concurrent.Future

@Singleton
private[callback] class IssueUserSessionStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userSessionTokenWriter: UserSessionTokenWriter,
    sessionTokenService: SessionTokenService,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      userId: Long,
      now: BusinessDateTime
  ): Future[IssuedSessionToken] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val issuedSessionToken = sessionTokenService.issue(userId)
        userSessionTokenWriter.write(
          issuedSessionToken.toUserSessionTokenDbRow(now)
        )
        issuedSessionToken
      }
    }(using databaseExecutor)
}
