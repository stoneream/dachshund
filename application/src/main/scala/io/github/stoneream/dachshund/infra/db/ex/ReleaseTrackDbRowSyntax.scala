package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.ReleaseTrackDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.service.spotify.client.model.SpotifyReleaseTrack

object ReleaseTrackDbRowSyntax {

  extension (source: ReleaseTrackSource) {
    def toReleaseTrackDbRow: ReleaseTrackDbRow = {
      import DbRowValues.*

      ReleaseTrackDbRow(
        id = 0L,
        artistReleaseId = source.artistReleaseId,
        spotifyTrackCode = source.spotifyTrackCode,
        trackName = source.trackName,
        spotifyTrackUri = source.spotifyTrackUri,
        spotifyUrl = source.spotifyUrl,
        href = source.href,
        discNumber = source.discNumber,
        trackNumber = source.trackNumber,
        durationMs = source.durationMs,
        explicit = source.explicit,
        isPlayable = source.isPlayable,
        isLocal = source.isLocal,
        linkedFromSpotifyTrackCode = source.linkedFromSpotifyTrackCode,
        linkedFromSpotifyTrackUri = source.linkedFromSpotifyTrackUri,
        previewUrl = source.previewUrl,
        externalIdsJson = source.externalIdsJson,
        isrcCode = source.isrcCode,
        eanCode = source.eanCode,
        upcCode = source.upcCode,
        availableMarketsJson = source.availableMarketsJson,
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

  extension (track: SpotifyReleaseTrack) {
    def toReleaseTrackDbRow(artistReleaseId: Long, now: BusinessDateTime): ReleaseTrackDbRow =
      ReleaseTrackSource(
        artistReleaseId = artistReleaseId,
        spotifyTrackCode = track.spotifyTrackCode,
        trackName = track.trackName,
        spotifyTrackUri = track.spotifyTrackUri,
        spotifyUrl = track.spotifyUrl,
        href = track.href,
        discNumber = track.discNumber,
        trackNumber = track.trackNumber,
        durationMs = track.durationMs,
        explicit = track.explicit,
        isPlayable = track.isPlayable,
        isLocal = track.isLocal,
        linkedFromSpotifyTrackCode = track.linkedFromSpotifyTrackCode,
        linkedFromSpotifyTrackUri = track.linkedFromSpotifyTrackUri,
        previewUrl = track.previewUrl,
        externalIdsJson = track.externalIdsJson,
        isrcCode = track.isrcCode,
        eanCode = track.eanCode,
        upcCode = track.upcCode,
        availableMarketsJson = track.availableMarketsJson,
        restrictionsJson = track.restrictionsJson,
        popularity = track.popularity,
        syncedAt = Some(now),
        createdAt = now,
        updatedAt = now,
        deletedAt = Option.empty,
        createdUser = AuditUser.System,
        updatedUser = AuditUser.System,
        deletedUser = AuditUser.Empty,
        deleted = 0L,
        lockVersion = 0L
      ).toReleaseTrackDbRow
  }
}
