package io.github.stoneream.dachshund.infra.db.reader.artist_releases_sync

import com.google.inject.{Inject, Singleton}
import scalikejdbc.*

@Singleton
class ArtistReleasesReader @Inject() () {
  def findExistingSpotifyReleaseCodes(
      spotifyReleaseCodes: Seq[String]
  )(using DBSession): Set[String] = {
    val cleanedCodes = spotifyReleaseCodes.map(_.trim).filter(_.nonEmpty).distinct
    if (cleanedCodes.isEmpty) {
      Set.empty
    } else {
      sql"""
        select
          spotify_release_code
        from
          artist_release
        where
          ${sqls.in(sqls"spotify_release_code", cleanedCodes)}
      """
        .map(_.string("spotify_release_code"))
        .list
        .apply()
        .toSet
    }
  }

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
