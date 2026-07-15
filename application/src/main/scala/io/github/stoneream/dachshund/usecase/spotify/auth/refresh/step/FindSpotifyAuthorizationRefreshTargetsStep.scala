package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.reader.auth.refresh.SpotifyAuthorizationRefreshReader
import io.github.stoneream.dachshund.infra.db.reader.auth.refresh.SpotifyAuthorizationRefreshReader.RefreshTargetRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.SpotifyAccessTokenRefreshUseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.SpotifyAuthorizationRefreshTarget

import com.google.inject.{Inject, Singleton}
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.*

/**
 * access token refresh が必要な Spotify 認可情報を取得
 */
@Singleton
private[refresh] class FindSpotifyAuthorizationRefreshTargetsStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    refreshReader: SpotifyAuthorizationRefreshReader,
    databaseExecutor: DatabaseExecutor
) {
  private val processingLeaseSeconds = 3600L

  def run(
      now: BusinessDateTime,
      batchSize: Int
  ): Future[Seq[SpotifyAuthorizationRefreshTarget]] = {
    val lockToken = UUID.randomUUID().toString
    val lockedUntil = now.plus(processingLeaseSeconds.seconds)

    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        refreshReader.recoverStaleProcessingTargets(now = now)

        val claimResults = refreshReader.claimRefreshTargets(
          now = now,
          batchSize = batchSize,
          lockToken = lockToken,
          lockedUntil = lockedUntil
        )
        claimResults.find(!_.claimed).foreach { result =>
          throw SpotifyAccessTokenRefreshUseCaseException.RefreshTargetClaimFailed(result.target.queueId)
        }

        claimResults.map(result => toRefreshTarget(result.target))
      }
    }(using databaseExecutor)
  }

  private def toRefreshTarget(row: RefreshTargetRow): SpotifyAuthorizationRefreshTarget =
    SpotifyAuthorizationRefreshTarget(
      authorizationId = row.authorizationId,
      queueId = row.queueId,
      userId = row.userId,
      scopeText = row.scopeText,
      encryptedRefreshToken = row.encryptedRefreshToken,
      tokenType = row.tokenType,
      accessTokenExpiresAt = row.accessTokenExpiresAt,
      refreshMarginSeconds = row.refreshMarginSeconds,
      lastAuthorizedAt = row.lastAuthorizedAt,
      lastRefreshedAt = row.lastRefreshedAt,
      queueStatus = row.queueStatus,
      attemptCount = row.attemptCount,
      nextAttemptAt = row.nextAttemptAt,
      lastAttemptedAt = row.lastAttemptedAt,
      completedAt = row.completedAt,
      lastFailedAt = row.lastFailedAt,
      lastErrorType = row.lastErrorType,
      lockToken = row.lockToken,
      lockedUntil = row.lockedUntil,
      authorizationLockVersion = row.authorizationLockVersion,
      queueLockVersion = row.queueLockVersion,
      queueDeleted = row.queueDeleted
    )
}
