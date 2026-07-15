package io.github.stoneream.dachshund.service.application.artist_release_sync_queue

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.application.artist_release_sync_queue.model.ArtistReleaseSyncQueueTarget

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait ArtistReleaseSyncQueueService {

  /**
   * 実行可能な queue を取得し、同一 transaction で PROCESSING に遷移させる。
   *
   * 期限切れの PROCESSING queue は claim 前に SCHEDULED へ戻す。
   */
  def claimDueTargets(
      now: BusinessDateTime,
      batchSize: Int,
      processingLease: FiniteDuration
  ): Future[Seq[ArtistReleaseSyncQueueTarget]]

  /**
   * Spotify の 1 ページ分の同期成功を queue に反映する。
   *
   * completed が false の場合は次ページ取得用に SCHEDULED へ戻し、true の場合は SUCCEEDED として完了する。
   */
  def markPageProcessed(
      target: ArtistReleaseSyncQueueTarget,
      nextOffset: Int,
      completed: Boolean,
      now: BusinessDateTime
  ): Future[ArtistReleaseSyncQueueUpdateResult]

  /**
   * 再試行可能な失敗を記録し、queue を SCHEDULED へ戻す。
   */
  def markTemporaryFailure(
      target: ArtistReleaseSyncQueueTarget,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  ): Future[ArtistReleaseSyncQueueUpdateResult]

  /**
   * ユーザー操作または運用対応が必要な失敗を記録し、queue を BLOCKED にする。
   */
  def markBlocked(
      target: ArtistReleaseSyncQueueTarget,
      reasonType: String,
      now: BusinessDateTime
  ): Future[ArtistReleaseSyncQueueUpdateResult]

  /**
   * abort した同期 run で claim 済みの queue を再実行可能な状態へ戻す。
   */
  def releaseProcessingTargets(
      targets: Seq[ArtistReleaseSyncQueueTarget],
      now: BusinessDateTime
  ): Future[Int]
}
