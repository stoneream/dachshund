package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import com.neovisionaries.i18n.CountryCode
import io.circe.Json
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyImage, SpotifyReleaseTrack}
import se.michaelthelin.spotify.model_objects.miscellaneous.Restrictions
import se.michaelthelin.spotify.model_objects.specification.{Album, AlbumSimplified, Copyright, ExternalId, ExternalUrl, Image, TrackSimplified}

import scala.jdk.CollectionConverters.*

private[spotify_artist_release] object SpotifyArtistReleaseMapper {
  def toSummary(summary: AlbumSimplified): Option[SpotifyArtistReleaseSummary] =
    Option(summary.getId).map(_.trim).filter(_.nonEmpty).map { releaseCode =>
      SpotifyArtistReleaseSummary(
        spotifyReleaseCode = releaseCode,
        releaseName = Option(summary.getName).getOrElse(""),
        albumType = Option(summary.getAlbumType).map(_.getType).getOrElse(""),
        albumGroup = Option(summary.getAlbumGroup).map(_.getGroup),
        spotifyReleaseUri = Option(summary.getUri).getOrElse(""),
        spotifyUrl = Option(summary.getExternalUrls).flatMap(spotifyUrl).getOrElse(""),
        href = Option(summary.getHref).getOrElse(""),
        images = Option(summary.getImages).map(_.toSeq.map(toSpotifyImage)).getOrElse(Seq.empty),
        releaseDateText = Option(summary.getReleaseDate).getOrElse(""),
        releaseDatePrecision = Option(summary.getReleaseDatePrecision).map(_.getPrecision).getOrElse(""),
        restrictionsJson = Option(summary.getRestrictions).map(restrictionsJson)
      )
    }

  def toRelease(
      sourceSpotifyArtistCode: String,
      summary: SpotifyArtistReleaseSummary,
      detail: Album,
      tracks: Seq[TrackSimplified]
  ): SpotifyArtistRelease = {
    val images = Option(detail.getImages).map(_.toSeq.map(toSpotifyImage)).getOrElse(summary.images)
    val primaryImage = images.headOption
    val externalIds = externalIdMap(Option(detail.getExternalIds))
    val releaseDateText = Option(detail.getReleaseDate).getOrElse(summary.releaseDateText)
    val releaseDatePrecision = Option(detail.getReleaseDatePrecision)
      .map(_.getPrecision)
      .getOrElse(summary.releaseDatePrecision)
    val totalTracksCount = Some(tracks.size)
    val albumType = Option(detail.getAlbumType).map(_.getType).getOrElse(summary.albumType)
    val labelName = Option(detail.getLabel).map(_.trim).filter(_.nonEmpty)

    SpotifyArtistRelease(
      spotifyReleaseCode = Option(detail.getId).map(_.trim).filter(_.nonEmpty).getOrElse(summary.spotifyReleaseCode),
      sourceSpotifyArtistCode = sourceSpotifyArtistCode,
      releaseName = Option(detail.getName).getOrElse(summary.releaseName),
      releaseType = SpotifyReleaseType.fromAlbumType(albumType),
      albumType = albumType,
      albumGroup = summary.albumGroup,
      spotifyReleaseUri = Option(detail.getUri).getOrElse(summary.spotifyReleaseUri),
      spotifyUrl = Option(detail.getExternalUrls).flatMap(spotifyUrl).getOrElse(summary.spotifyUrl),
      href = Option(detail.getHref).getOrElse(summary.href),
      primaryImageUrl = primaryImage.map(_.url).getOrElse(""),
      primaryImageHeight = primaryImage.flatMap(_.height),
      primaryImageWidth = primaryImage.flatMap(_.width),
      imagesJson = nonEmptyJsonArray(images.map(imageJson)),
      releaseDateText = releaseDateText,
      releaseDatePrecision = releaseDatePrecision,
      releaseDateAt = SpotifyReleaseDate.releaseDateAt(releaseDateText, releaseDatePrecision),
      totalTracksCount = totalTracksCount,
      labelName = labelName,
      normalizedLabelName = labelName.map(normalizeLabelName),
      externalIdsJson = nonEmptyJsonObject(externalIds),
      upcCode = externalIds.get("upc"),
      eanCode = externalIds.get("ean"),
      isrcCode = externalIds.get("isrc"),
      copyrightsJson = nonEmptyJsonArray(Option(detail.getCopyrights).map(_.toSeq).getOrElse(Seq.empty).map(copyrightJson)),
      availableMarketsJson = nonEmptyJsonArray(Option(detail.getAvailableMarkets).map(_.toSeq).getOrElse(Seq.empty).map(countryJson)),
      genresJson = nonEmptyJsonArray(Option(detail.getGenres).map(_.toSeq).getOrElse(Seq.empty).map(Json.fromString)),
      restrictionsJson = summary.restrictionsJson,
      popularity = Option(detail.getPopularity).map(_.toInt),
      tracks = tracks.flatMap(toReleaseTrack)
    )
  }

  private def toReleaseTrack(track: TrackSimplified): Option[SpotifyReleaseTrack] =
    Option(track.getId).map(_.trim).filter(_.nonEmpty).map { trackCode =>
      SpotifyReleaseTrack(
        spotifyTrackCode = trackCode,
        trackName = Option(track.getName).getOrElse(""),
        spotifyTrackUri = Option(track.getUri).getOrElse(""),
        spotifyUrl = Option(track.getExternalUrls).flatMap(spotifyUrl).getOrElse(""),
        href = Option(track.getHref).getOrElse(""),
        discNumber = Option(track.getDiscNumber).map(_.toInt).getOrElse(0),
        trackNumber = Option(track.getTrackNumber).map(_.toInt).getOrElse(0),
        durationMs = Option(track.getDurationMs).map(_.toInt),
        explicit = Option(track.getIsExplicit).map(value => booleanLong(value.booleanValue())),
        isPlayable = Option(track.getIsPlayable).map(value => booleanLong(value.booleanValue())),
        isLocal = Option.empty[Long],
        linkedFromSpotifyTrackCode = Option(track.getLinkedFrom).flatMap(linkedFrom => Option(linkedFrom.getId)),
        linkedFromSpotifyTrackUri = Option(track.getLinkedFrom).flatMap(linkedFrom => Option(linkedFrom.getUri)),
        previewUrl = Option(track.getPreviewUrl),
        externalIdsJson = Option.empty[String],
        isrcCode = Option.empty[String],
        eanCode = Option.empty[String],
        upcCode = Option.empty[String],
        availableMarketsJson = nonEmptyJsonArray(Option(track.getAvailableMarkets).map(_.toSeq).getOrElse(Seq.empty).map(countryJson)),
        restrictionsJson = Option.empty[String],
        popularity = Option.empty[Int]
      )
    }

  private def spotifyUrl(externalUrl: ExternalUrl): Option[String] =
    Option(externalUrl.get("spotify")).map(_.trim).filter(_.nonEmpty)

  private def externalIdMap(externalId: Option[ExternalId]): Map[String, String] =
    externalId
      .flatMap(value => Option(value.getExternalIds))
      .map(_.asScala.toMap.view.mapValues(Option(_).getOrElse("")).toMap)
      .getOrElse(Map.empty)

  private def normalizeLabelName(labelName: String): String =
    labelName.trim.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ")

  private def booleanLong(value: Boolean): Long =
    if (value) 1L else 0L

  private def toSpotifyImage(image: Image): SpotifyImage =
    SpotifyImage(
      url = Option(image.getUrl).getOrElse(""),
      height = Option(image.getHeight).map(_.toInt),
      width = Option(image.getWidth).map(_.toInt)
    )

  private def imageJson(image: SpotifyImage): Json =
    Json.obj(
      "url" -> Option(image.url).map(Json.fromString).getOrElse(Json.Null),
      "height" -> image.height.map(Json.fromInt).getOrElse(Json.Null),
      "width" -> image.width.map(Json.fromInt).getOrElse(Json.Null)
    )

  private def copyrightJson(copyright: Copyright): Json =
    Json.obj(
      "text" -> Option(copyright.getText).map(Json.fromString).getOrElse(Json.Null),
      "type" -> Option(copyright.getType).map(value => Json.fromString(value.getType)).getOrElse(Json.Null)
    )

  private def countryJson(countryCode: CountryCode): Json =
    Json.fromString(countryCode.name())

  private def restrictionsJson(restrictions: Restrictions): String =
    Json
      .obj(
        "reason" -> Option(restrictions.getReason).map(Json.fromString).getOrElse(Json.Null)
      )
      .noSpaces

  private def nonEmptyJsonArray(values: Seq[Json]): Option[String] =
    Option.when(values.nonEmpty)(Json.arr(values*).noSpaces)

  private def nonEmptyJsonObject(values: Map[String, String]): Option[String] =
    Option.when(values.nonEmpty) {
      Json
        .obj(values.toSeq.sortBy(_._1).map { case (key, value) =>
          key -> Json.fromString(value)
        }*)
        .noSpaces
    }
}
