package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.{DatabaseExecutor, DefaultExecutor}
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.user_profile_client.SpotifyUserProfileClient.CurrentUserProfile
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.auth.callback.SpotifyAuthorizationReader
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * SpotifyユーザーIDに紐づく既存ユーザーを解決
 */
@Singleton
private[callback] class ResolveSpotifyUserStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    spotifyAuthorizationReader: SpotifyAuthorizationReader,
    databaseExecutor: DatabaseExecutor,
    defaultExecutor: DefaultExecutor
) extends TraceLogger {

  import ResolveSpotifyUserStep.*

  def run(
      spotifyProfile: CurrentUserProfile
  )(using LoggingContext): Future[Option[ResolvedSpotifyUser]] = {
    given DefaultExecutor = defaultExecutor

    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        spotifyAuthorizationReader.findUserIdBySpotifyUserId(spotifyProfile.id)
      }
    }(using databaseExecutor)
      .map {
        case Some(userId) =>
          info(
            "既存の Spotify ユーザーを特定しました",
            kv("userId", userId),
            kv("spotifyUserId", mask(spotifyProfile.id))
          )
          Some(ResolvedSpotifyUser(userId = userId))
        case None =>
          None
      }
      .recoverWith { case NonFatal(e) =>
        Future.failed(UseCaseException.AuthorizationPersistenceFailed(e))
      }(using defaultExecutor)
  }
}

private[callback] object ResolveSpotifyUserStep {
  final case class ResolvedSpotifyUser(
      userId: Long
  )
}
