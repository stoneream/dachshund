package io.github.stoneream.dachshund.infra.db.writer

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.UserNewReleaseEventDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.{ArtistReleaseSource, UserNewReleaseEventSource, UserSource}
import io.github.stoneream.dachshund.infra.db.generated.{ArtistReleaseDbRow, UserDbRow, UserNewReleaseEventDbRow}
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.test.lib.db.DatabaseSupport
import org.scalatest.featurespec.AnyFeatureSpec
import scalikejdbc.*

import java.time.LocalDate

class UserNewReleaseEventsWriterSpec extends AnyFeatureSpec with DatabaseSupport {
  private val userWriter = new SpotifyUserWriter
  private val artistReleasesWriter = new ArtistReleasesWriter
  private val userNewReleaseEventsWriter = new UserNewReleaseEventsWriter

  Feature("User new release events writer") {
    Scenario("新規作成されたユーザー別新着リリース履歴 ID だけを返す") {
      val (firstResult, secondResult, eventCount) = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(Rows.userRow)
        val artistReleaseId = artistReleasesWriter.write(Rows.artistReleaseRow)
        val eventRow = Rows.eventRow(userId, artistReleaseId)

        val firstResult = userNewReleaseEventsWriter.writeIfAbsentReturningId(eventRow)
        val secondResult = userNewReleaseEventsWriter.writeIfAbsentReturningId(eventRow)
        val eventCount = sql"select count(*) as count from user_new_release_event".map(_.int("count")).single.apply().get

        (firstResult, secondResult, eventCount)
      }

      assert(firstResult.nonEmpty)
      assert(secondResult.isEmpty)
      assert(eventCount == 1)
    }
  }

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")

  private object Rows {
    val userRow: UserDbRow =
      UserSource(
        userName = "writer-user",
        displayName = "Writer User",
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

    val artistReleaseRow: ArtistReleaseDbRow =
      ArtistReleaseSource(
        spotifyReleaseCode = "writer-release",
        sourceSpotifyArtistCode = "writer-artist",
        releaseName = "Writer Release",
        releaseType = "ALBUM",
        albumType = "album",
        albumGroup = Some("album"),
        spotifyReleaseUri = "spotify:album:writer-release",
        spotifyUrl = "",
        href = "",
        primaryImageUrl = "",
        primaryImageHeight = None,
        primaryImageWidth = None,
        imagesJson = None,
        releaseDateText = "2026-06-20",
        releaseDatePrecision = "day",
        releaseDateAt = Some(LocalDate.of(2026, 6, 20).atStartOfDay()),
        totalTracksCount = None,
        labelName = Some("Writer Label"),
        normalizedLabelName = Some("writer label"),
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

    def eventRow(
        userId: Long,
        artistReleaseId: Long
    ): UserNewReleaseEventDbRow =
      UserNewReleaseEventSource(
        userId = userId,
        artistReleaseId = artistReleaseId,
        spotifyReleaseCode = "writer-release",
        sourceSpotifyArtistCode = "writer-artist",
        detectedAt = fixedNow,
        detectionSyncCode = "writer-spec",
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = Option.empty,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toUserNewReleaseEventDbRow
  }
}
