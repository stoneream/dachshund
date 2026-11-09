package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import io.circe.Decoder

import java.util.Locale

private[spotify_artist_release] object SpotifyArtistReleaseResponse {
  final case class PageResponse[A](
      items: Option[Seq[A]],
      next: Option[String],
      limit: Option[Int],
      offset: Option[Int]
  ) {
    def values: Seq[A] = items.getOrElse(Seq.empty)

    def nextOffset: Option[Int] =
      Option.when(next.exists(_.trim.nonEmpty))(offset.getOrElse(0) + limit.getOrElse(values.size))
  }

  object PageResponse {
    given [A: Decoder]: Decoder[PageResponse[A]] =
      Decoder.forProduct4("items", "next", "limit", "offset")(PageResponse[A].apply)
  }

  final case class AlbumResponse(
      id: Option[String],
      name: Option[String],
      albumType: Option[String],
      albumGroup: Option[String],
      uri: Option[String],
      externalUrls: Option[Map[String, Option[String]]],
      href: Option[String],
      images: Option[Seq[ImageResponse]],
      releaseDate: Option[String],
      releaseDatePrecision: Option[String],
      restrictions: Option[RestrictionsResponse],
      label: Option[String],
      externalIds: Option[Map[String, Option[String]]],
      copyrights: Option[Seq[CopyrightResponse]],
      availableMarkets: Option[Seq[String]],
      genres: Option[Seq[String]],
      popularity: Option[Int],
      tracks: Option[PageResponse[TrackResponse]]
  )

  object AlbumResponse {
    given Decoder[AlbumResponse] =
      Decoder
        .forProduct18(
          "id",
          "name",
          "album_type",
          "album_group",
          "uri",
          "external_urls",
          "href",
          "images",
          "release_date",
          "release_date_precision",
          "restrictions",
          "label",
          "external_ids",
          "copyrights",
          "available_markets",
          "genres",
          "popularity",
          "tracks"
        )(AlbumResponse.apply)
        .map { album =>
          album.copy(
            albumType = normalizedEnum(album.albumType, Set("album", "compilation", "single")),
            releaseDatePrecision = normalizedEnum(album.releaseDatePrecision, Set("day", "month", "year"))
          )
        }
  }

  final case class TrackResponse(
      id: Option[String],
      name: Option[String],
      uri: Option[String],
      externalUrls: Option[Map[String, Option[String]]],
      href: Option[String],
      discNumber: Option[Int],
      trackNumber: Option[Int],
      durationMs: Option[Int],
      explicit: Option[Boolean],
      isPlayable: Option[Boolean],
      linkedFrom: Option[LinkedFromResponse],
      previewUrl: Option[String],
      availableMarkets: Option[Seq[String]]
  )

  object TrackResponse {
    given Decoder[TrackResponse] =
      Decoder.forProduct13(
        "id",
        "name",
        "uri",
        "external_urls",
        "href",
        "disc_number",
        "track_number",
        "duration_ms",
        "explicit",
        "is_playable",
        "linked_from",
        "preview_url",
        "available_markets"
      )(TrackResponse.apply)
  }

  final case class ImageResponse(url: Option[String], height: Option[Int], width: Option[Int])

  object ImageResponse {
    given Decoder[ImageResponse] = Decoder.forProduct3("url", "height", "width")(ImageResponse.apply)
  }

  final case class LinkedFromResponse(id: Option[String], uri: Option[String])

  object LinkedFromResponse {
    given Decoder[LinkedFromResponse] = Decoder.forProduct2("id", "uri")(LinkedFromResponse.apply)
  }

  final case class CopyrightResponse(text: Option[String], copyrightType: Option[String])

  object CopyrightResponse {
    given Decoder[CopyrightResponse] =
      Decoder.forProduct2("text", "type")(CopyrightResponse.apply).map { copyright =>
        copyright.copy(copyrightType = normalizedEnum(copyright.copyrightType, Set("c", "p")))
      }
  }

  final case class RestrictionsResponse(reason: Option[String])

  object RestrictionsResponse {
    given Decoder[RestrictionsResponse] = Decoder.forProduct1("reason")(RestrictionsResponse.apply)
  }

  private def normalizedEnum(value: Option[String], allowed: Set[String]): Option[String] =
    value.map(_.toLowerCase(Locale.ROOT)).filter(allowed)

}
