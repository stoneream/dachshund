package io.github.stoneream.dachshund.usecase.spotify.artist_releases_sync.step

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.artist_releases_sync.ArtistReleasesReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.SpotifyArtistReleaseSummary

import scala.concurrent.Future

@Singleton
private[artist_releases_sync] class FindUnsyncedArtistReleaseSummariesStep @Inject() (
    databaseTransaction: DatabaseTransaction,
    artistReleasesReader: ArtistReleasesReader,
    databaseExecutor: DatabaseExecutor
) {
  def run(
      summaries: Seq[SpotifyArtistReleaseSummary]
  ): Future[Seq[SpotifyArtistReleaseSummary]] =
    Future {
      databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
        val existingCodes = artistReleasesReader.findExistingSpotifyReleaseCodes(
          summaries.map(_.spotifyReleaseCode)
        )
        summaries
          .distinctBy(_.spotifyReleaseCode)
          .filterNot(summary => existingCodes.contains(summary.spotifyReleaseCode))
      }
    }(using databaseExecutor)
}
