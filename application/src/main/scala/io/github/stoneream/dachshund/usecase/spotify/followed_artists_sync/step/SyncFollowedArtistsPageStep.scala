package io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.UserFollowedArtistDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.reader.followed_artists_sync.UserFollowedArtistsReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.UserFollowedArtistsWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model.SpotifyFollowedArtistsPage
import io.github.stoneream.dachshund.usecase.spotify.followed_artists_sync.context.UserFollowedArtistsSyncResult

import scala.concurrent.Future

/**
 * 取得済みのフォロー中 artist 1 ページを user_followed_artist に反映する step。
 *
 * syncDate の marker を last_seen_at に保存し、最終ページでは今回見えなかった既存行を削除扱いにする。
 * Spotify API 取得と queue 状態更新は呼び出し元の責務。
 */
@Singleton
private[followed_artists_sync] class SyncFollowedArtistsPageStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    userFollowedArtistsReader: UserFollowedArtistsReader,
    userFollowedArtistsWriter: UserFollowedArtistsWriter,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      target: FollowedArtistSyncQueueTarget,
      page: SpotifyFollowedArtistsPage,
      now: BusinessDateTime
  ): Future[UserFollowedArtistsSyncResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val seenMarkerAt = BusinessDateTime.from(
          target.syncDate.atStartOfDay().atOffset(now.asOffsetDateTime.getOffset)
        )
        val upsertedCount = page.artists.map { artist =>
          userFollowedArtistsWriter.write(
            artist.toUserFollowedArtistDbRow(
              userId = target.userId,
              firstFollowedAt = now,
              lastSeenAt = seenMarkerAt,
              lastSyncedAt = now
            )
          )
        }.sum
        val deletedCount =
          if (page.isLastPage) {
            userFollowedArtistsReader
              .findDeletionTargetsForUpdate(
                userId = target.userId,
                lastSeenAt = seenMarkerAt.toLocalDateTime
              )
              .count { updateTarget =>
                userFollowedArtistsWriter.update(
                  id = updateTarget.id,
                  userId = updateTarget.userId,
                  expectedLockVersion = updateTarget.lockVersion,
                  deleted = 1L,
                  deletedAt = now,
                  deletedUser = AuditUser.System,
                  lastSyncedAt = now,
                  updatedAt = now,
                  updatedUser = AuditUser.System,
                  lockVersion = updateTarget.lockVersion + 1L
                )
              }
          } else {
            0
          }

        UserFollowedArtistsSyncResult(
          upsertedCount = upsertedCount,
          deletedCount = deletedCount
        )
      }
    }(using databaseExecutor)
}
