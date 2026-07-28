package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.ex.ArtistReleaseDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.ex.ReleaseTrackDbRowSyntax.*
import io.github.stoneream.dachshund.infra.db.reader.artist_releases_sync.ArtistReleasesReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.infra.db.writer.ArtistReleasesWriter
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.SpotifyArtistRelease
import io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.context.ArtistReleasePageSyncResult
import scalikejdbc.DBSession

import scala.concurrent.Future

@Singleton
private[artist_releases_sync] class SyncArtistReleasePageStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    artistReleasesReader: ArtistReleasesReader,
    artistReleasesWriter: ArtistReleasesWriter,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      releases: Seq[SpotifyArtistRelease],
      now: BusinessDateTime
  ): Future[ArtistReleasePageSyncResult] =
    Future {
      databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val trackCount = releases.flatMap(saveNewRelease(_, now))
        ArtistReleasePageSyncResult(
          releaseCount = trackCount.size,
          trackCount = trackCount.sum
        )
      }
    }(using databaseExecutor)

  private def saveNewRelease(
      release: SpotifyArtistRelease,
      now: BusinessDateTime
  )(using DBSession): Option[Int] =
    artistReleasesReader.findIdBySpotifyReleaseCode(release.spotifyReleaseCode) match {
      case Some(_) =>
        Option.empty
      case None =>
        val artistReleaseId = writeRelease(release, now)
        writeTracks(artistReleaseId, release, now)
        Some(release.tracks.size)
    }

  private def writeRelease(
      release: SpotifyArtistRelease,
      now: BusinessDateTime
  )(using DBSession): Long =
    artistReleasesWriter.write(
      release.toArtistReleaseDbRow(now)
    )

  private def writeTracks(
      artistReleaseId: Long,
      release: SpotifyArtistRelease,
      now: BusinessDateTime
  )(using DBSession): Unit =
    release.tracks.foreach { track =>
      artistReleasesWriter.writeReleaseTrack(
        track.toReleaseTrackDbRow(artistReleaseId, now)
      )
    }
}
