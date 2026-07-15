package io.github.stoneream.dachshund.auth

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.auth.UserSessionContext.NotLoggedIn
import io.github.stoneream.dachshund.infra.db.reader.auth.UserSessionTokenReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor

import scala.concurrent.Future

@Singleton
class UserSessionContextResolver @Inject() (
    databaseTransaction: DatabaseTransaction,
    userSessionTokenReader: UserSessionTokenReader,
    sessionTokenService: SessionTokenService,
    databaseExecutor: DatabaseExecutor
) {
  def resolve(
      sessionToken: Option[String],
      now: BusinessDateTime
  ): Future[UserSessionContext] = {
    val trimmedToken = sessionToken.map(_.trim).filter(_.nonEmpty).getOrElse("")
    sessionTokenService.verify(trimmedToken) match {
      case Left(_) =>
        Future.successful(NotLoggedIn)
      case Right(parsedToken) =>
        Future {
          val hashedToken = sessionTokenService.lookupHash(parsedToken)
          databaseTransaction
            .readOnly(DatabaseRole.Master) { implicit session =>
              userSessionTokenReader.findUserByHashedToken(hashedToken, now)
            }
            .getOrElse(NotLoggedIn)
        }(using databaseExecutor)
    }
  }
}
