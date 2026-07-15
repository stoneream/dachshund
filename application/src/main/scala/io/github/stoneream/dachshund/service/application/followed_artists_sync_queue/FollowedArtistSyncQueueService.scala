package io.github.stoneream.dachshund.service.application.followed_artists_sync_queue

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.application.followed_artists_sync_queue.model.FollowedArtistSyncQueueTarget

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait FollowedArtistSyncQueueService {

  /**
   * 実行可能な queue を取得し、同一 transaction で PROCESSING に遷移させる。
   *
   * 期限切れの PROCESSING queue は claim 前に SCHEDULED へ戻す。
   */
  def claimDueTargets(
      now: BusinessDateTime,
      batchSize: Int,
      processingLease: FiniteDuration
  ): Future[Seq[FollowedArtistSyncQueueTarget]]

  /**
   * Spotify のページ同期完了を queue に反映する。
   *
   * 全ページ同期では最終ページで呼び出し、SUCCEEDED として完了する。
   */
  def markPageProcessed(
      target: FollowedArtistSyncQueueTarget,
      nextAfterCursor: Option[String],
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueUpdateResult]

  /**
   * Spotify の中間ページ同期成功を queue に反映する。
   *
   * queue は PROCESSING のまま保持し、同じ run で次ページを取得できるように after_cursor と lock_version を進める。
   */
  def markPageProgressed(
      target: FollowedArtistSyncQueueTarget,
      nextAfterCursor: String,
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueProgressResult]

  /**
   * 再試行可能な失敗を記録し、queue を SCHEDULED へ戻す。
   */
  def markTemporaryFailure(
      target: FollowedArtistSyncQueueTarget,
      failureType: String,
      nextAttemptAt: BusinessDateTime,
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueUpdateResult]

  /**
   * ユーザー操作または運用対応が必要な失敗を記録し、queue を BLOCKED にする。
   */
  def markBlocked(
      target: FollowedArtistSyncQueueTarget,
      reasonType: String,
      now: BusinessDateTime
  ): Future[FollowedArtistSyncQueueUpdateResult]

  /**
   * abort した同期 run で claim 済みの queue を再実行可能な状態へ戻す。
   */
  def releaseProcessingTargets(
      targets: Seq[FollowedArtistSyncQueueTarget],
      now: BusinessDateTime
  ): Future[Int]
}
