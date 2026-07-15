package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserSpotifyAuthDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.{SpotifyUserAuthWriter, SpotifyUserWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.user_profile_client.SpotifyUserProfileClient.CurrentUserProfile
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Spotify認証済みIDに紐づく新規ユーザーと認証識別子を作成
 */
@Singleton
private[callback] class CreateNewUserStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    spotifyUserWriter: SpotifyUserWriter,
    spotifyUserAuthWriter: SpotifyUserAuthWriter,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {

  import CreateNewUserStep.*

  def run(
      spotifyProfile: CurrentUserProfile,
      now: BusinessDateTime
  )(using LoggingContext): Future[CreatedUser] = {
    given DefaultExecutor = defaultExecutor

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = spotifyUserWriter.write(
          spotifyProfile.toUserDbRow(now)
        )
        spotifyUserAuthWriter.write(
          spotifyProfile.toUserSpotifyAuthDbRow(userId, now)
        )

        CreatedUser(
          userId = userId
        )
      }
    }(using databaseExecutor)
      .map { createdUser =>
        info(
          "新しい Spotify ユーザーを作成しました",
          kv("userId", createdUser.userId),
          kv("spotifyUserId", mask(spotifyProfile.id))
        )
        createdUser
      }
      .recoverWith { case NonFatal(e) =>
        Future.failed(UseCaseException.AuthorizationPersistenceFailed(e))
      }(using defaultExecutor)
  }
}

private[callback] object CreateNewUserStep {
  final case class CreatedUser(
      userId: Long
  )
}
