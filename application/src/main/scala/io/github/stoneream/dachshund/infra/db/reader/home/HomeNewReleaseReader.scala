package io.github.stoneream.dachshund.infra.db.reader.home

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.infra.db.reader.home.HomeNewReleaseReader.HomeNewReleaseRow
import scalikejdbc.*

import java.time.{LocalDate, LocalDateTime}

@Singleton
class HomeNewReleaseReader @Inject() () {
  def findRecentReleases(
      userId: Long,
      releasedFrom: LocalDateTime,
      releasedTo: LocalDateTime,
      limit: Int
  )(using DBSession): Seq[HomeNewReleaseRow] =
    sql"""
      select
        ar.id,
        ar.release_name,
        ar.release_type,
        ar.release_date_at,
        ar.label_name,
        ar.spotify_url,
        ar.primary_image_url,
        ar.source_spotify_artist_code,
        coalesce(nullif(ufa.artist_name, ''), ar.source_spotify_artist_code) as source_artist_name
      from
        user_new_release_event unre
        inner join artist_release ar on ar.id = unre.artist_release_id
        left join user_followed_artist ufa
          on ufa.user_id = unre.user_id
          and ufa.spotify_artist_code = ar.source_spotify_artist_code
      where
        unre.user_id = {userId}
        and unre.deleted = 0
        and ar.deleted = 0
        and ar.release_date_precision = 'day'
        and ar.release_date_at >= {releasedFrom}
        and ar.release_date_at <= {releasedTo}
      order by
        ar.release_date_at desc,
        ar.release_name asc,
        ar.id asc
      limit {limit}
    """
      .bindByName(
        "userId" -> userId,
        "releasedFrom" -> releasedFrom,
        "releasedTo" -> releasedTo,
        "limit" -> limit
      )
      .map { rs =>
        HomeNewReleaseRow(
          artistReleaseId = rs.long("id"),
          releaseName = rs.string("release_name"),
          releaseType = rs.string("release_type"),
          releaseDate = rs.localDateTime("release_date_at").toLocalDate,
          labelName = rs.stringOpt("label_name"),
          spotifyUrl = rs.string("spotify_url"),
          primaryImageUrl = rs.string("primary_image_url"),
          sourceSpotifyArtistCode = rs.string("source_spotify_artist_code"),
          sourceArtistName = rs.string("source_artist_name")
        )
      }
      .list
      .apply()
}

object HomeNewReleaseReader {
  final case class HomeNewReleaseRow(
      artistReleaseId: Long,
      releaseName: String,
      releaseType: String,
      releaseDate: LocalDate,
      labelName: Option[String],
      spotifyUrl: String,
      primaryImageUrl: String,
      sourceSpotifyArtistCode: String,
      sourceArtistName: String
  )
}
