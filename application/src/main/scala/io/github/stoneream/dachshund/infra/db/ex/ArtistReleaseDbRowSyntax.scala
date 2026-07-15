package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.ArtistReleaseDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyArtistRelease

object ArtistReleaseDbRowSyntax {

  extension (source: ArtistReleaseSource) {
    def toArtistReleaseDbRow: ArtistReleaseDbRow = {
      import DbRowValues.*

      ArtistReleaseDbRow(
        id = 0L,
        spotifyReleaseCode = source.spotifyReleaseCode,
        sourceSpotifyArtistCode = source.sourceSpotifyArtistCode,
        releaseName = source.releaseName,
        releaseType = source.releaseType,
        albumType = source.albumType,
        albumGroup = source.albumGroup,
        spotifyReleaseUri = source.spotifyReleaseUri,
        spotifyUrl = source.spotifyUrl,
        href = source.href,
        primaryImageUrl = source.primaryImageUrl,
        primaryImageHeight = source.primaryImageHeight,
        primaryImageWidth = source.primaryImageWidth,
        imagesJson = source.imagesJson,
        releaseDateText = source.releaseDateText,
        releaseDatePrecision = source.releaseDatePrecision,
        releaseDateAt = source.releaseDateAt,
        totalTracksCount = source.totalTracksCount,
        labelName = source.labelName,
        normalizedLabelName = source.normalizedLabelName,
        externalIdsJson = source.externalIdsJson,
        upcCode = source.upcCode,
        eanCode = source.eanCode,
        isrcCode = source.isrcCode,
        copyrightsJson = source.copyrightsJson,
        availableMarketsJson = source.availableMarketsJson,
        genresJson = source.genresJson,
        restrictionsJson = source.restrictionsJson,
        popularity = source.popularity,
        syncedAt = source.syncedAt.dbDateTime,
        createdAt = source.createdAt.dbDateTime,
        updatedAt = source.updatedAt.dbDateTime,
        deletedAt = source.deletedAt.dbDateTime,
        createdUser = source.createdUser.dbAuditUser,
        updatedUser = source.updatedUser.dbAuditUser,
        deletedUser = source.deletedUser.dbAuditUser,
        deleted = source.deleted,
        lockVersion = source.lockVersion
      )
    }
  }

  extension (release: SpotifyArtistRelease) {
    def toArtistReleaseDbRow(now: BusinessDateTime): ArtistReleaseDbRow =
      ArtistReleaseSource(
        spotifyReleaseCode = release.spotifyReleaseCode,
        sourceSpotifyArtistCode = release.sourceSpotifyArtistCode,
        releaseName = release.releaseName,
        releaseType = release.releaseType,
        albumType = release.albumType,
        albumGroup = release.albumGroup,
        spotifyReleaseUri = release.spotifyReleaseUri,
        spotifyUrl = release.spotifyUrl,
        href = release.href,
        primaryImageUrl = release.primaryImageUrl,
        primaryImageHeight = release.primaryImageHeight,
        primaryImageWidth = release.primaryImageWidth,
        imagesJson = release.imagesJson,
        releaseDateText = release.releaseDateText,
        releaseDatePrecision = release.releaseDatePrecision,
        releaseDateAt = release.releaseDateAt,
        totalTracksCount = release.totalTracksCount,
        labelName = release.labelName,
        normalizedLabelName = release.normalizedLabelName,
        externalIdsJson = release.externalIdsJson,
        upcCode = release.upcCode,
        eanCode = release.eanCode,
        isrcCode = release.isrcCode,
        copyrightsJson = release.copyrightsJson,
        availableMarketsJson = release.availableMarketsJson,
        genresJson = release.genresJson,
        restrictionsJson = release.restrictionsJson,
        popularity = release.popularity,
        syncedAt = Some(now),
        createdAt = now,
        updatedAt = now,
        deletedAt = Option.empty,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toArtistReleaseDbRow
  }
}
