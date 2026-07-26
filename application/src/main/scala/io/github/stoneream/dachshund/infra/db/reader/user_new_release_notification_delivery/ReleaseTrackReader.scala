package io.github.stoneream.dachshund.infra.db.reader.user_new_release_notification_delivery

import com.google.inject.{Inject, Singleton}
import scalikejdbc.*

@Singleton
class ReleaseTrackReader @Inject() () {
  def findSpotifyTrackUrisByArtistReleaseId(
      artistReleaseId: Long
  )(using DBSession): Seq[String] =
    sql"""
      select
        spotify_track_uri
      from
        release_track
      where
        artist_release_id = {artistReleaseId}
        and spotify_track_uri <> ''
        and deleted = 0
      order by
        disc_number asc,
        track_number asc,
        id asc
    """
      .bindByName(
        "artistReleaseId" -> artistReleaseId
      )
      .map(_.string("spotify_track_uri"))
      .list
      .apply()
}
