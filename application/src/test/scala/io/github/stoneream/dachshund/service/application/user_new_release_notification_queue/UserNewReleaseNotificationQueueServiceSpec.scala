package io.github.stoneream.dachshund.service.application.user_new_release_notification_queue

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseNotificationQueueDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserPlaylistSettingDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSource, UserNewReleaseNotificationQueueSource, UserPlaylistSettingSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.{ArtistReleaseDbRow, UserDbRow, UserNewReleaseNotificationQueueDbRow, UserPlaylistSettingDbRow}
import io.github.stoneream.dachshund.infra.db.reader.user_new_release_notification_queue.UserNewReleaseNotificationQueueReader
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleasesWriter, SpotifyUserWriter, UserNewReleaseNotificationQueueWriter, UserPlaylistSettingWriter}
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.{PlaylistUsageType, QueueJobStatus, ReleaseNotificationType}
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.UserNewReleaseNotificationQueueUpdateResult.Updated
import io.github.stoneream.dachshund.service.application.user_new_release_notification_queue.model.UserNewReleaseNotificationQueueTarget
import io.github.stoneream.dachshund.test.lib.db.DatabaseSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.LocalDateTime
import scala.concurrent.duration.*

class UserNewReleaseNotificationQueueServiceSpec extends AnyFeatureSpec with ScalaFutures with DatabaseSupport {
  private val userWriter = new SpotifyUserWriter
  private val artistReleasesWriter = new ArtistReleasesWriter
  private val playlistSettingWriter = new UserPlaylistSettingWriter
  private val queueWriter = new UserNewReleaseNotificationQueueWriter
  private val service = new UserNewReleaseNotificationQueueServiceImpl(
    databaseTransaction = databaseTransaction,
    queueReader = new UserNewReleaseNotificationQueueReader,
    queueWriter = queueWriter,
    databaseExecutor = databaseExecutor
  )

  Feature("User new release notification queue service") {
    Scenario("claim 前に stale queue を復旧して playlist target を返す") {
      val (scheduledEventId, staleEventId, playlistCode) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("claim-user"))
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId))

        val scheduledReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("scheduled-release-code"))
        val scheduledEventId = writeNewReleaseEvent(userId, scheduledReleaseId, "scheduled-release-code")
        queueWriter.write(Rows.scheduledQueueRow(scheduledEventId, playlistSettingId))

        val staleReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("stale-release-code"))
        val staleEventId = writeNewReleaseEvent(userId, staleReleaseId, "stale-release-code")
        queueWriter.write(Rows.staleQueueRow(staleEventId, playlistSettingId))

        (scheduledEventId, staleEventId, Rows.PlaylistCode)
      }

      val result = service
        .claimDueTargets(fixedNow, releaseNotificationType = ReleaseNotificationType.Playlist, batchSize = 25, processingLease = 30.minutes)
        .futureValue

      assert(result.size == 2)
      val scheduledClaimed = result.find(_.userNewReleaseEventId == scheduledEventId).get
      assert(scheduledClaimed.status == QueueJobStatus.Processing)
      assert(scheduledClaimed.attemptCount == 1)
      assert(scheduledClaimed.lockToken.nonEmpty)
      assert(scheduledClaimed.queueLockVersion == 1L)
      assert(scheduledClaimed.spotifyPlaylistCode == playlistCode)
      assert(scheduledClaimed.releaseNotificationType == ReleaseNotificationType.Playlist)
      assert(scheduledClaimed.lastAttemptedAt.map(_.toLocalDateTime) == Some(fixedNow.toLocalDateTime))
      assert(scheduledClaimed.lockedUntil.map(_.toLocalDateTime) == Some(fixedNow.plus(30.minutes).toLocalDateTime))

      val staleClaimed = result.find(_.userNewReleaseEventId == staleEventId).get
      assert(staleClaimed.status == QueueJobStatus.Processing)
      assert(staleClaimed.attemptCount == 2)
      assert(staleClaimed.lockToken.nonEmpty)
      assert(staleClaimed.queueLockVersion == 4L)

      val rows = queueRows()
      val scheduledRow = rows.find(_.userNewReleaseEventId == scheduledEventId).get
      val staleRow = rows.find(_.userNewReleaseEventId == staleEventId).get
      assert(scheduledRow.status == QueueJobStatus.Processing.dbValue)
      assert(scheduledRow.lockToken.nonEmpty)
      assert(scheduledRow.lockedUntil == Some(fixedNow.plus(30.minutes).toLocalDateTime))
      assert(scheduledRow.lockVersion == 1L)
      assert(staleRow.status == QueueJobStatus.Processing.dbValue)
      assert(staleRow.lockToken.nonEmpty)
      assert(staleRow.lockedUntil == Some(fixedNow.plus(30.minutes).toLocalDateTime))
      assert(staleRow.lockVersion == 4L)
    }

    Scenario("重複した playlist 通知キューは作成しない") {
      val (eventId, playlistSettingId) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("duplicate-user"))
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId))
        val releaseId = artistReleasesWriter.write(Rows.artistReleaseRow("duplicate-release-code"))
        val eventId = writeNewReleaseEvent(userId, releaseId, "duplicate-release-code")
        (eventId, playlistSettingId)
      }

      val written = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val first = queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId))
        val second = queueWriter.write(Rows.scheduledQueueRow(eventId, playlistSettingId))
        (first, second)
      }

      assert(written == (1, 0))
      assert(queueRows().count(_.userNewReleaseEventId == eventId) == 1)
    }

    Scenario("未到来 queue と無効な playlist setting は claim しない") {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val futureUserId = userWriter.write(Rows.userRow("future-user"))
        val enabledPlaylistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(futureUserId))
        val futureReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("future-release-code"))
        val futureEventId = writeNewReleaseEvent(futureUserId, futureReleaseId, "future-release-code")
        queueWriter.write(Rows.futureQueueRow(futureEventId, enabledPlaylistSettingId))

        val disabledUserId = userWriter.write(Rows.userRow("disabled-playlist-setting-user"))
        val disabledPlaylistSettingId = playlistSettingWriter.write(
          Rows.playlistSettingRow(
            userId = disabledUserId,
            playlistUsageType = PlaylistUsageType.NewReleaseNotification,
            spotifyPlaylistCode = "disabled-playlist-code",
            enabled = 0L
          )
        )
        val disabledReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("disabled-release-code"))
        val disabledEventId = writeNewReleaseEvent(disabledUserId, disabledReleaseId, "disabled-release-code")
        queueWriter.write(Rows.scheduledQueueRow(disabledEventId, disabledPlaylistSettingId))
      }

      val result = service
        .claimDueTargets(fixedNow, releaseNotificationType = ReleaseNotificationType.Playlist, batchSize = 25, processingLease = 30.minutes)
        .futureValue

      assert(result.isEmpty)
    }

    Scenario("成功、一時失敗、blocked を保存する") {
      val (succeededEventId, temporaryFailureEventId, blockedEventId) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow("update-user"))
        val playlistSettingId = playlistSettingWriter.write(Rows.playlistSettingRow(userId))

        val succeededReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("succeeded-release-code"))
        val succeededEventId = writeNewReleaseEvent(userId, succeededReleaseId, "succeeded-release-code")
        queueWriter.write(Rows.processingQueueRow(succeededEventId, playlistSettingId, "succeeded-lock-token", 5L))

        val temporaryFailureReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("temporary-failure-release-code"))
        val temporaryFailureEventId =
          writeNewReleaseEvent(userId, temporaryFailureReleaseId, "temporary-failure-release-code")
        queueWriter.write(Rows.processingQueueRow(temporaryFailureEventId, playlistSettingId, "temporary-failure-lock-token", 6L))

        val blockedReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow("blocked-release-code"))
        val blockedEventId = writeNewReleaseEvent(userId, blockedReleaseId, "blocked-release-code")
        queueWriter.write(Rows.processingQueueRow(blockedEventId, playlistSettingId, "blocked-lock-token", 7L))

        (succeededEventId, temporaryFailureEventId, blockedEventId)
      }

      val succeededTarget = findQueueTarget(succeededEventId)
      val temporaryFailureTarget = findQueueTarget(temporaryFailureEventId)
      val blockedTarget = findQueueTarget(blockedEventId)
      val nextAttemptAt = fixedNow.plus(10.minutes)

      val succeededResult = service.markSucceeded(succeededTarget, "snapshot-id", fixedNow).futureValue
      val temporaryFailureResult =
        service.markTemporaryFailure(temporaryFailureTarget, "rate_limited", nextAttemptAt, fixedNow).futureValue
      val blockedResult = service.markBlocked(blockedTarget, "insufficient_scope", fixedNow).futureValue

      assert(succeededResult == Updated)
      assert(temporaryFailureResult == Updated)
      assert(blockedResult == Updated)
      val rows = queueRows()
      val succeededRow = rows.find(_.userNewReleaseEventId == succeededEventId).get
      val temporaryFailureRow = rows.find(_.userNewReleaseEventId == temporaryFailureEventId).get
      val blockedRow = rows.find(_.userNewReleaseEventId == blockedEventId).get
      assert(succeededRow.status == QueueJobStatus.Succeeded.dbValue)
      assert(succeededRow.completedAt == Some(fixedNow.toLocalDateTime))
      assert(succeededRow.spotifySnapshotId == "snapshot-id")
      assert(succeededRow.lockToken == "")
      assert(succeededRow.lockVersion == 6L)
      assert(temporaryFailureRow.status == QueueJobStatus.Scheduled.dbValue)
      assert(temporaryFailureRow.nextAttemptAt == Some(nextAttemptAt.toLocalDateTime))
      assert(temporaryFailureRow.lastFailedAt == Some(fixedNow.toLocalDateTime))
      assert(temporaryFailureRow.lastErrorType == "rate_limited")
      assert(temporaryFailureRow.lockVersion == 7L)
      assert(blockedRow.status == QueueJobStatus.Blocked.dbValue)
      assert(blockedRow.nextAttemptAt.isEmpty)
      assert(blockedRow.lastFailedAt == Some(fixedNow.toLocalDateTime))
      assert(blockedRow.lastErrorType == "insufficient_scope")
      assert(blockedRow.lockVersion == 8L)
    }

    Scenario("未知の release notification type は失敗する") {
      assertThrows[IllegalArgumentException] {
        ReleaseNotificationType.fromDbValue("UNKNOWN")
      }
    }
  }

  private final case class QueueRow(
      userNewReleaseEventId: Long,
      status: String,
      nextAttemptAt: Option[LocalDateTime],
      completedAt: Option[LocalDateTime],
      attemptCount: Int,
      lastFailedAt: Option[LocalDateTime],
      lastErrorType: String,
      lockToken: String,
      lockedUntil: Option[LocalDateTime],
      spotifySnapshotId: String,
      lockVersion: Long
  )

  private def writeNewReleaseEvent(
      userId: Long,
      artistReleaseId: Long,
      spotifyReleaseCode: String
  )(using DBSession): Long =
    sql"""
      insert into user_new_release_event (
        user_id,
        artist_release_id,
        spotify_release_code,
        source_spotify_artist_code,
        detected_at,
        detection_sync_code,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {userId},
        {artistReleaseId},
        {spotifyReleaseCode},
        {sourceSpotifyArtistCode},
        {detectedAt},
        {detectionSyncCode},
        {createdAt},
        {updatedAt},
        {deletedAt},
        {createdUser},
        {updatedUser},
        {deletedUser},
        {deleted},
        {lockVersion}
      )
    """
      .bindByName(
        "userId" -> userId,
        "artistReleaseId" -> artistReleaseId,
        "spotifyReleaseCode" -> spotifyReleaseCode,
        "sourceSpotifyArtistCode" -> "source-artist-code",
        "detectedAt" -> fixedNow.toLocalDateTime,
        "detectionSyncCode" -> "test",
        "createdAt" -> fixedNow.toLocalDateTime,
        "updatedAt" -> fixedNow.toLocalDateTime,
        "deletedAt" -> Option.empty[LocalDateTime],
        "createdUser" -> AuditUser.System.dbValue,
        "updatedUser" -> AuditUser.System.dbValue,
        "deletedUser" -> AuditUser.Empty.dbValue,
        "deleted" -> 0L,
        "lockVersion" -> 0L
      )
      .updateAndReturnGeneratedKey
      .apply()

  private def findQueueTarget(userNewReleaseEventId: Long): UserNewReleaseNotificationQueueTarget =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          q.id as queue_id,
          q.user_new_release_event_id,
          une.user_id,
          une.artist_release_id,
          une.spotify_release_code,
          q.release_notification_type,
          q.playlist_setting_id,
          ups.spotify_playlist_code,
          q.status,
          q.next_attempt_at,
          q.attempt_count,
          q.last_failed_at,
          q.last_error_type,
          q.lock_token,
          q.locked_until,
          q.last_attempted_at,
          q.completed_at,
          q.spotify_snapshot_id,
          q.deleted_at,
          q.deleted,
          q.lock_version
        from
          user_new_release_notification_queue q
          inner join user_new_release_event une on une.id = q.user_new_release_event_id
          inner join user_playlist_setting ups on ups.id = q.playlist_setting_id
        where
          q.user_new_release_event_id = {userNewReleaseEventId}
      """
        .bindByName("userNewReleaseEventId" -> userNewReleaseEventId)
        .map { rs =>
          UserNewReleaseNotificationQueueTarget(
            queueId = rs.long("queue_id"),
            userNewReleaseEventId = rs.long("user_new_release_event_id"),
            userId = rs.long("user_id"),
            artistReleaseId = rs.long("artist_release_id"),
            spotifyReleaseCode = rs.string("spotify_release_code"),
            releaseNotificationType = ReleaseNotificationType.fromDbValue(rs.string("release_notification_type")),
            playlistSettingId = rs.long("playlist_setting_id"),
            spotifyPlaylistCode = rs.string("spotify_playlist_code"),
            status = QueueJobStatus.fromDbValue(rs.string("status")),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at").map(BusinessDateTime.fromLocalDateTime),
            attemptCount = rs.int("attempt_count"),
            lastFailedAt = rs.localDateTimeOpt("last_failed_at").map(BusinessDateTime.fromLocalDateTime),
            lastErrorType = rs.string("last_error_type"),
            lockToken = rs.string("lock_token"),
            lockedUntil = rs.localDateTimeOpt("locked_until").map(BusinessDateTime.fromLocalDateTime),
            lastAttemptedAt = rs.localDateTimeOpt("last_attempted_at").map(BusinessDateTime.fromLocalDateTime),
            completedAt = rs.localDateTimeOpt("completed_at").map(BusinessDateTime.fromLocalDateTime),
            spotifySnapshotId = rs.string("spotify_snapshot_id"),
            deletedAt = rs.localDateTimeOpt("deleted_at").map(BusinessDateTime.fromLocalDateTime),
            deletedUser = AuditUser.Empty,
            deleted = rs.long("deleted"),
            queueLockVersion = rs.long("lock_version")
          )
        }
        .single
        .apply()
        .get
    }

  private def queueRows(): Seq[QueueRow] =
    databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
      sql"""
        select
          user_new_release_event_id,
          status,
          next_attempt_at,
          completed_at,
          attempt_count,
          last_failed_at,
          last_error_type,
          lock_token,
          locked_until,
          spotify_snapshot_id,
          lock_version
        from user_new_release_notification_queue
        order by id asc
      """
        .map { rs =>
          QueueRow(
            userNewReleaseEventId = rs.long("user_new_release_event_id"),
            status = rs.string("status"),
            nextAttemptAt = rs.localDateTimeOpt("next_attempt_at"),
            completedAt = rs.localDateTimeOpt("completed_at"),
            attemptCount = rs.int("attempt_count"),
            lastFailedAt = rs.localDateTimeOpt("last_failed_at"),
            lastErrorType = rs.string("last_error_type"),
            lockToken = rs.string("lock_token"),
            lockedUntil = rs.localDateTimeOpt("locked_until"),
            spotifySnapshotId = rs.string("spotify_snapshot_id"),
            lockVersion = rs.long("lock_version")
          )
        }
        .list
        .apply()
    }

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  private object Rows {
    val PlaylistCode = "playlist-code"

    def userRow(userName: String): UserDbRow =
      UserSource(
        userName = userName,
        displayName = userName,
        timeZone = "Asia/Tokyo",
        enabled = 1L,
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserDbRow

    def artistReleaseRow(spotifyReleaseCode: String): ArtistReleaseDbRow =
      ArtistReleaseSource(
        spotifyReleaseCode = spotifyReleaseCode,
        sourceSpotifyArtistCode = "source-artist-code",
        releaseName = spotifyReleaseCode,
        releaseType = "ALBUM",
        albumType = "album",
        albumGroup = Some("album"),
        spotifyReleaseUri = s"spotify:album:$spotifyReleaseCode",
        spotifyUrl = "",
        href = "",
        primaryImageUrl = "",
        primaryImageHeight = None,
        primaryImageWidth = None,
        imagesJson = None,
        releaseDateText = "2026-06-21",
        releaseDatePrecision = "day",
        releaseDateAt = Some(fixedNow.toLocalDateTime),
        totalTracksCount = Some(1),
        labelName = None,
        normalizedLabelName = None,
        externalIdsJson = None,
        upcCode = None,
        eanCode = None,
        isrcCode = None,
        copyrightsJson = None,
        availableMarketsJson = None,
        genresJson = None,
        restrictionsJson = None,
        popularity = None,
        syncedAt = Some(fixedNow),
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toArtistReleaseDbRow

    def playlistSettingRow(
        userId: Long,
        playlistUsageType: PlaylistUsageType = PlaylistUsageType.NewReleaseNotification,
        spotifyPlaylistCode: String = PlaylistCode,
        enabled: Long = 1L
    ): UserPlaylistSettingDbRow =
      UserPlaylistSettingSource(
        userId = userId,
        playlistUsageType = playlistUsageType,
        spotifyPlaylistCode = spotifyPlaylistCode,
        spotifyPlaylistUri = s"spotify:playlist:$spotifyPlaylistCode",
        playlistName = spotifyPlaylistCode,
        enabled = enabled,
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserPlaylistSettingDbRow

    def scheduledQueueRow(userNewReleaseEventId: Long, playlistSettingId: Long): UserNewReleaseNotificationQueueDbRow =
      queueRow(
        userNewReleaseEventId = userNewReleaseEventId,
        playlistSettingId = playlistSettingId,
        status = QueueJobStatus.Scheduled,
        nextAttemptAt = Some(fixedNow),
        attemptCount = 0,
        lockToken = "",
        lockedUntil = None,
        lockVersion = 0L
      )

    def futureQueueRow(userNewReleaseEventId: Long, playlistSettingId: Long): UserNewReleaseNotificationQueueDbRow =
      scheduledQueueRow(userNewReleaseEventId, playlistSettingId).copy(nextAttemptAt = Some(fixedNow.plus(1.hour).toLocalDateTime))

    def staleQueueRow(userNewReleaseEventId: Long, playlistSettingId: Long): UserNewReleaseNotificationQueueDbRow =
      queueRow(
        userNewReleaseEventId = userNewReleaseEventId,
        playlistSettingId = playlistSettingId,
        status = QueueJobStatus.Processing,
        nextAttemptAt = Some(fixedNow.minus(10.minutes)),
        attemptCount = 1,
        lockToken = "stale-lock-token",
        lockedUntil = Some(fixedNow.minus(1.minute)),
        lockVersion = 2L
      )

    def processingQueueRow(
        userNewReleaseEventId: Long,
        playlistSettingId: Long,
        lockToken: String,
        lockVersion: Long
    ): UserNewReleaseNotificationQueueDbRow =
      queueRow(
        userNewReleaseEventId = userNewReleaseEventId,
        playlistSettingId = playlistSettingId,
        status = QueueJobStatus.Processing,
        nextAttemptAt = Some(fixedNow.minus(1.minute)),
        attemptCount = 2,
        lockToken = lockToken,
        lockedUntil = Some(fixedNow.plus(30.minutes)),
        lockVersion = lockVersion
      )

    private def queueRow(
        userNewReleaseEventId: Long,
        playlistSettingId: Long,
        status: QueueJobStatus,
        nextAttemptAt: Option[BusinessDateTime],
        attemptCount: Int,
        lockToken: String,
        lockedUntil: Option[BusinessDateTime],
        lockVersion: Long
    ): UserNewReleaseNotificationQueueDbRow =
      UserNewReleaseNotificationQueueSource(
        userNewReleaseEventId = userNewReleaseEventId,
        releaseNotificationType = ReleaseNotificationType.Playlist,
        playlistSettingId = playlistSettingId,
        status = status,
        nextAttemptAt = nextAttemptAt,
        attemptCount = attemptCount,
        lastFailedAt = None,
        lastErrorType = "",
        lockToken = lockToken,
        lockedUntil = lockedUntil,
        lastAttemptedAt = Some(fixedNow),
        completedAt = None,
        spotifySnapshotId = "",
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = None,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = lockVersion
      ).toUserNewReleaseNotificationQueueDbRow
  }
}
