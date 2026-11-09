package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import io.circe.Json
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.SpotifyArtistReleaseResponse.*
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyImage, SpotifyReleaseTrack}

private[spotify_artist_release] object SpotifyArtistReleaseMapper {
  def toSummary(summary: AlbumResponse): Option[SpotifyArtistReleaseSummary] =
    summary.id.map(_.trim).filter(_.nonEmpty).map { releaseCode =>
      SpotifyArtistReleaseSummary(
        spotifyReleaseCode = releaseCode,
        releaseName = summary.name.getOrElse(""),
        albumType = summary.albumType.getOrElse(""),
        albumGroup = summary.albumGroup,
        spotifyReleaseUri = summary.uri.getOrElse(""),
        spotifyUrl = spotifyUrl(summary.externalUrls).getOrElse(""),
        href = summary.href.getOrElse(""),
        images = summary.images.toSeq.flatten.map(toSpotifyImage),
        releaseDateText = summary.releaseDate.getOrElse(""),
        releaseDatePrecision = summary.releaseDatePrecision.getOrElse(""),
        restrictionsJson = summary.restrictions.map(restrictionsJson)
      )
    }

  def toRelease(
      sourceSpotifyArtistCode: String,
      summary: SpotifyArtistReleaseSummary,
      detail: AlbumResponse,
      tracks: Seq[TrackResponse]
  ): SpotifyArtistRelease = {
    val images = detail.images.fold(summary.images)(_.map(toSpotifyImage))
    val primaryImage = images.headOption
    val externalIds = detail.externalIds.getOrElse(Map.empty).view.mapValues(_.getOrElse("")).toMap
    val releaseDateText = detail.releaseDate.getOrElse(summary.releaseDateText)
    val releaseDatePrecision = detail.releaseDatePrecision.getOrElse(summary.releaseDatePrecision)
    val albumType = detail.albumType.getOrElse(summary.albumType)
    val labelName = detail.label.map(_.trim).filter(_.nonEmpty)

    SpotifyArtistRelease(
      spotifyReleaseCode = detail.id.map(_.trim).filter(_.nonEmpty).getOrElse(summary.spotifyReleaseCode),
      sourceSpotifyArtistCode = sourceSpotifyArtistCode,
      releaseName = detail.name.getOrElse(summary.releaseName),
      releaseType = SpotifyReleaseType.fromAlbumType(albumType),
      albumType = albumType,
      albumGroup = summary.albumGroup,
      spotifyReleaseUri = detail.uri.getOrElse(summary.spotifyReleaseUri),
      spotifyUrl = spotifyUrl(detail.externalUrls).getOrElse(summary.spotifyUrl),
      href = detail.href.getOrElse(summary.href),
      primaryImageUrl = primaryImage.map(_.url).getOrElse(""),
      primaryImageHeight = primaryImage.flatMap(_.height),
      primaryImageWidth = primaryImage.flatMap(_.width),
      imagesJson = nonEmptyJsonArray(images.map(imageJson)),
      releaseDateText = releaseDateText,
      releaseDatePrecision = releaseDatePrecision,
      releaseDateAt = SpotifyReleaseDate.releaseDateAt(releaseDateText, releaseDatePrecision),
      totalTracksCount = Some(tracks.size),
      labelName = labelName,
      normalizedLabelName = labelName.map(normalizeLabelName),
      externalIdsJson = nonEmptyJsonObject(externalIds),
      upcCode = externalIds.get("upc"),
      eanCode = externalIds.get("ean"),
      isrcCode = externalIds.get("isrc"),
      copyrightsJson = nonEmptyJsonArray(detail.copyrights.toSeq.flatten.map(copyrightJson)),
      availableMarketsJson = nonEmptyJsonArray(detail.availableMarkets.toSeq.flatten.map(Json.fromString)),
      genresJson = nonEmptyJsonArray(detail.genres.toSeq.flatten.map(Json.fromString)),
      restrictionsJson = summary.restrictionsJson,
      popularity = detail.popularity,
      tracks = tracks.flatMap(toReleaseTrack)
    )
  }

  private def toReleaseTrack(track: TrackResponse): Option[SpotifyReleaseTrack] =
    track.id.map(_.trim).filter(_.nonEmpty).map { trackCode =>
      SpotifyReleaseTrack(
        spotifyTrackCode = trackCode,
        trackName = track.name.getOrElse(""),
        spotifyTrackUri = track.uri.getOrElse(""),
        spotifyUrl = spotifyUrl(track.externalUrls).getOrElse(""),
        href = track.href.getOrElse(""),
        discNumber = track.discNumber.getOrElse(0),
        trackNumber = track.trackNumber.getOrElse(0),
        durationMs = track.durationMs,
        explicit = track.explicit.map(booleanLong),
        isPlayable = track.isPlayable.map(booleanLong),
        isLocal = None,
        linkedFromSpotifyTrackCode = track.linkedFrom.flatMap(_.id),
        linkedFromSpotifyTrackUri = track.linkedFrom.flatMap(_.uri),
        previewUrl = track.previewUrl,
        externalIdsJson = None,
        isrcCode = None,
        eanCode = None,
        upcCode = None,
        availableMarketsJson = nonEmptyJsonArray(track.availableMarkets.toSeq.flatten.map(Json.fromString)),
        restrictionsJson = None,
        popularity = None
      )
    }

  private def spotifyUrl(externalUrls: Option[Map[String, Option[String]]]): Option[String] =
    externalUrls.flatMap(_.get("spotify").flatten).map(_.trim).filter(_.nonEmpty)

  private def normalizeLabelName(labelName: String): String =
    labelName.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ")

  private def booleanLong(value: Boolean): Long =
    if (value) 1L else 0L

  private def toSpotifyImage(image: ImageResponse): SpotifyImage =
    SpotifyImage(url = image.url.getOrElse(""), height = image.height, width = image.width)

  private def imageJson(image: SpotifyImage): Json =
    Json.obj(
      "url" -> Json.fromString(image.url),
      "height" -> image.height.map(Json.fromInt).getOrElse(Json.Null),
      "width" -> image.width.map(Json.fromInt).getOrElse(Json.Null)
    )

  private def copyrightJson(copyright: CopyrightResponse): Json =
    Json.obj(
      "text" -> copyright.text.map(Json.fromString).getOrElse(Json.Null),
      "type" -> copyright.copyrightType.map(Json.fromString).getOrElse(Json.Null)
    )

  private def restrictionsJson(restrictions: RestrictionsResponse): String =
    Json.obj("reason" -> restrictions.reason.map(Json.fromString).getOrElse(Json.Null)).noSpaces

  private def nonEmptyJsonArray(values: Seq[Json]): Option[String] =
    Option.when(values.nonEmpty)(Json.arr(values*).noSpaces)

  private def nonEmptyJsonObject(values: Map[String, String]): Option[String] =
    Option.when(values.nonEmpty) {
      Json.obj(values.toSeq.sortBy(_._1).map { case (key, value) => key -> Json.fromString(value) }*).noSpaces
    }
}
