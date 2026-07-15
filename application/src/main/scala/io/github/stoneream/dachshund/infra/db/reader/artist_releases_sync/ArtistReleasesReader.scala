package io.github.stoneream.dachshund.infra.db.reader.artist_releases_sync

import com.google.inject.{Inject, Singleton}
import scalikejdbc.*

@Singleton
class ArtistReleasesReader @Inject() () {
  def findIdBySpotifyReleaseCode(
      spotifyReleaseCode: String
  )(using DBSession): Option[Long] =
    sql"""
      select
        id
      from
        artist_release
      where
        spotify_release_code = {spotifyReleaseCode}
      limit 1
    """
      .bindByName("spotifyReleaseCode" -> spotifyReleaseCode)
      .map(_.long("id"))
      .single
      .apply()
}
