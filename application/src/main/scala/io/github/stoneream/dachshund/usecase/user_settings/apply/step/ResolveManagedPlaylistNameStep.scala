package io.github.stoneream.dachshund.usecase.user_settings.apply.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClient
import io.github.stoneream.dachshund.usecase.user_settings.UserSettingsManagedPlaylist

import java.util.UUID
import scala.concurrent.Future

@Singleton
private[apply] class ResolveManagedPlaylistNameStep @Inject() (
    spotifyClient: SpotifyClient,
    defaultExecutor: DefaultExecutor
) {
  private val spotifyPlaylistPageLimit = 50

  def run(
      accessToken: String
  )(using LoggingContext): Future[String] =
    loadPlaylistNames(accessToken, offset = 0, names = Set.empty).map { playlistNames =>
      if (playlistNames.contains(UserSettingsManagedPlaylist.BaseName)) {
        s"${UserSettingsManagedPlaylist.BaseName}_${UUID.randomUUID().toString}"
      } else {
        UserSettingsManagedPlaylist.BaseName
      }
    }(using defaultExecutor)

  private def loadPlaylistNames(
      accessToken: String,
      offset: Int,
      names: Set[String]
  )(using LoggingContext): Future[Set[String]] =
    spotifyClient
      .getCurrentUserPlaylistPage(accessToken, spotifyPlaylistPageLimit, offset)
      .flatMap { page =>
        val nextNames = names ++ page.playlists.map(_.playlistName.trim).filter(_.nonEmpty)
        page.nextOffset match {
          case Some(nextOffset) => loadPlaylistNames(accessToken, nextOffset, nextNames)
          case None => Future.successful(nextNames)
        }
      }(using defaultExecutor)
}
