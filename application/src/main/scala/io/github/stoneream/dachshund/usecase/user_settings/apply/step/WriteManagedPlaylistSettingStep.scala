package io.github.stoneream.dachshund.usecase.user_settings.apply.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingSource
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.UserPlaylistSettingWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.model.SpotifyCreatePlaylistResult
import io.github.stoneream.dachshund.usecase.user_settings.UserSettingsManagedPlaylist

import scala.concurrent.Future

@Singleton
private[apply] class WriteManagedPlaylistSettingStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userPlaylistSettingWriter: UserPlaylistSettingWriter,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      userId: Long,
      createdPlaylist: SpotifyCreatePlaylistResult,
      now: BusinessDateTime
  ): Future[Int] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        userPlaylistSettingWriter.writeIfAbsent(
          UserPlaylistSettingSource(
            userId = userId,
            playlistUsageType = UserSettingsManagedPlaylist.UsageType,
            spotifyPlaylistCode = createdPlaylist.spotifyPlaylistCode,
            spotifyPlaylistUri = spotifyPlaylistUri(createdPlaylist),
            playlistName = playlistName(createdPlaylist),
            enabled = 1L,
            createdAt = now,
            updatedAt = now,
            deletedAt = Option.empty,
            createdUser = AuditUser.User(userId),
            updatedUser = AuditUser.User(userId),
            deletedUser = AuditUser.Empty,
            deleted = 0L,
            lockVersion = 0L
          ).toUserPlaylistSettingDbRow
        )
      }
    }(using databaseExecutor)

  private def playlistName(createdPlaylist: SpotifyCreatePlaylistResult): String =
    Option(createdPlaylist.playlistName).map(_.trim).filter(_.nonEmpty).getOrElse(UserSettingsManagedPlaylist.BaseName)

  private def spotifyPlaylistUri(createdPlaylist: SpotifyCreatePlaylistResult): String =
    Option(createdPlaylist.spotifyPlaylistUri)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(s"spotify:playlist:${createdPlaylist.spotifyPlaylistCode}")
}
