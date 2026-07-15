package io.github.stoneream.dachshund.service.spotify.client

import com.google.inject.{Inject, Singleton}
import com.neovisionaries.i18n.CountryCode
import io.circe.Json
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.model.{SpotifyFollowedArtist, SpotifyFollowedArtistsPage}
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.service.spotify.client.model.{SpotifyArtistRelease, SpotifyArtistReleasePage, SpotifyReleaseTrack}
import org.apache.hc.core5.http.ParseException
import se.michaelthelin.spotify.enums.ModelObjectType
import se.michaelthelin.spotify.exceptions.detailed.{BadGatewayException, ForbiddenException, InternalServerErrorException, ServiceUnavailableException, TooManyRequestsException, UnauthorizedException}
import se.michaelthelin.spotify.model_objects.miscellaneous.Restrictions
import se.michaelthelin.spotify.model_objects.specification.{Album, AlbumSimplified, Artist, Copyright, ExternalId, ExternalUrl, Image, Paging, PagingCursorbased, TrackSimplified}
import se.michaelthelin.spotify.{SpotifyApi, SpotifyHttpManager}

import java.io.IOException
import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class SpotifyClientImpl @Inject() (
    applicationConfig: ApplicationConfig,
    ioDispatcher: IoDispatcher
) extends SpotifyClient
    with TraceLogger {
  private val clientConfig = applicationConfig.spotify.client
  private val httpManager =
    new SpotifyHttpManager.Builder()
      .setConnectionRequestTimeout(toMillisInt(clientConfig.connectTimeout.toMillis))
      .setSocketTimeout(toMillisInt(clientConfig.requestTimeout.toMillis))
      .build()

  override def getFollowedArtists(
      accessToken: String,
      afterCursor: Option[String],
      limit: Int
  )(using LoggingContext): Future[SpotifyFollowedArtistsPage] =
    Future {
      val builder = buildSpotifyApiClient(accessToken)
        .getUsersFollowedArtists(ModelObjectType.ARTIST)
        .limit(limit)

      afterCursor.foreach(builder.after)

      toFollowedArtistsPage(builder.build().execute())
    }(using ioDispatcher).recoverWith { case NonFatal(exception) =>
      Future.failed(classify(exception))
    }(using ioDispatcher)

  override def getArtistReleasePage(
      accessToken: String,
      spotifyArtistCode: String,
      includeGroups: String,
      market: Option[String],
      limit: Int,
      offset: Int
  )(using LoggingContext): Future[SpotifyArtistReleasePage] =
    Future {
      val spotifyApi = buildSpotifyApiClient(accessToken)
      val builder = spotifyApi
        .getArtistsAlbums(spotifyArtistCode)
        .include_groups(includeGroups)
        .limit(limit)
        .offset(offset)

      market.foreach(value => builder.market(countryCode(value)))

      val page = builder.build().execute()
      val releases = pageItems(page).flatMap { album =>
        Option(album.getId).map(_.trim).filter(_.nonEmpty).map { releaseCode =>
          val detail = getAlbum(spotifyApi, releaseCode, market)
          val tracks = getAlbumTracks(spotifyApi, detail, market)
          toArtistRelease(
            sourceSpotifyArtistCode = spotifyArtistCode,
            summary = album,
            detail = detail,
            tracks = tracks
          )
        }
      }

      SpotifyArtistReleasePage(
        releases = releases,
        nextOffset = nextOffset(page)
      )
    }(using ioDispatcher).recoverWith { case NonFatal(exception) =>
      Future.failed(classify(exception))
    }(using ioDispatcher)

  private def buildSpotifyApiClient(accessToken: String): SpotifyApi =
    SpotifyApi
      .builder()
      .setAccessToken(accessToken)
      .setHttpManager(httpManager)
      .build()

  private def toFollowedArtistsPage(paging: PagingCursorbased[Artist]): SpotifyFollowedArtistsPage = {
    val hasNext = Option(paging.getNext).exists(_.trim.nonEmpty)
    val nextAfterCursor =
      if (hasNext) {
        cursorAfter(paging).orElse(nextUrlAfter(paging.getNext)).orElse {
          throw ClientException.InvalidResponse(new IllegalStateException("Spotify followed artists response has next page without after cursor"))
        }
      } else {
        Option.empty[String]
      }

    SpotifyFollowedArtistsPage(
      artists = Option(paging.getItems).map(_.toSeq.flatMap(toFollowedArtist)).getOrElse(Seq.empty),
      nextAfterCursor = nextAfterCursor
    )
  }

  private def toFollowedArtist(artist: Artist): Option[SpotifyFollowedArtist] =
    Option(artist.getId).map(_.trim).filter(_.nonEmpty).map { artistId =>
      val images = Option(artist.getImages).map(_.toSeq).getOrElse(Seq.empty)
      val primaryImage = images.headOption
      val genres = Option(artist.getGenres).map(_.toSeq).getOrElse(Seq.empty)

      SpotifyFollowedArtist(
        spotifyArtistCode = artistId,
        artistName = Option(artist.getName).getOrElse(""),
        spotifyArtistUri = Option(artist.getUri).getOrElse(""),
        spotifyUrl = Option(artist.getExternalUrls).flatMap(urls => Option(urls.get("spotify"))).getOrElse(""),
        href = Option(artist.getHref).getOrElse(""),
        primaryImageUrl = primaryImage.flatMap(image => Option(image.getUrl)).getOrElse(""),
        primaryImageHeight = primaryImage.flatMap(image => Option(image.getHeight).map(_.toInt)),
        primaryImageWidth = primaryImage.flatMap(image => Option(image.getWidth).map(_.toInt)),
        imagesJson = nonEmptyJsonArray(images.map(imageJson)),
        genresJson = nonEmptyJsonArray(genres.map(Json.fromString)),
        followersTotal = Option(artist.getFollowers).flatMap(followers => Option(followers.getTotal).map(_.toLong)),
        popularity = Option(artist.getPopularity).map(_.toInt)
      )
    }

  private def getAlbum(
      spotifyApi: SpotifyApi,
      releaseCode: String,
      market: Option[String]
  ): Album = {
    val builder = spotifyApi.getAlbum(releaseCode)
    market.foreach(value => builder.market(countryCode(value)))
    builder.build().execute()
  }

  private def getAlbumTracks(
      spotifyApi: SpotifyApi,
      album: Album,
      market: Option[String]
  ): Seq[TrackSimplified] = {
    var currentPage = Option(album.getTracks)
    var tracks = currentPage.map(pageItems).getOrElse(Seq.empty)

    while (currentPage.exists(hasNextPage)) {
      val builder = spotifyApi
        .getAlbumsTracks(album.getId)
        .limit(50)
        .offset(nextPageOffset(currentPage.get))
      market.foreach(value => builder.market(countryCode(value)))

      val nextPage = builder.build().execute()
      tracks = tracks ++ pageItems(nextPage)
      currentPage = Some(nextPage)
    }

    tracks
  }

  private def toArtistRelease(
      sourceSpotifyArtistCode: String,
      summary: AlbumSimplified,
      detail: Album,
      tracks: Seq[TrackSimplified]
  ): SpotifyArtistRelease = {
    val images = Option(detail.getImages).map(_.toSeq).getOrElse(Option(summary.getImages).map(_.toSeq).getOrElse(Seq.empty))
    val primaryImage = images.headOption
    val externalIds = externalIdMap(Option(detail.getExternalIds))
    val releaseDateText = Option(detail.getReleaseDate).orElse(Option(summary.getReleaseDate)).getOrElse("")
    val releaseDatePrecision = Option(detail.getReleaseDatePrecision)
      .map(_.getPrecision)
      .orElse(Option(summary.getReleaseDatePrecision).map(_.getPrecision))
      .getOrElse("")
    val totalTracksCount = Some(tracks.size)
    val albumType = Option(detail.getAlbumType).map(_.getType).orElse(Option(summary.getAlbumType).map(_.getType)).getOrElse("")
    val labelName = Option(detail.getLabel).map(_.trim).filter(_.nonEmpty)

    SpotifyArtistRelease(
      spotifyReleaseCode = Option(detail.getId).map(_.trim).filter(_.nonEmpty).getOrElse(summary.getId),
      sourceSpotifyArtistCode = sourceSpotifyArtistCode,
      releaseName = Option(detail.getName).orElse(Option(summary.getName)).getOrElse(""),
      releaseType = SpotifyReleaseType.fromAlbumType(albumType),
      albumType = albumType,
      albumGroup = Option(summary.getAlbumGroup).map(_.getGroup),
      spotifyReleaseUri = Option(detail.getUri).orElse(Option(summary.getUri)).getOrElse(""),
      spotifyUrl = Option(detail.getExternalUrls).flatMap(spotifyUrl).orElse(Option(summary.getExternalUrls).flatMap(spotifyUrl)).getOrElse(""),
      href = Option(detail.getHref).orElse(Option(summary.getHref)).getOrElse(""),
      primaryImageUrl = primaryImage.flatMap(image => Option(image.getUrl)).getOrElse(""),
      primaryImageHeight = primaryImage.flatMap(image => Option(image.getHeight).map(_.toInt)),
      primaryImageWidth = primaryImage.flatMap(image => Option(image.getWidth).map(_.toInt)),
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
      restrictionsJson = Option(summary.getRestrictions).map(restrictionsJson),
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

  private def pageItems[A](paging: Paging[A]): Seq[A] =
    Option(paging)
      .flatMap(page => Option(page.getItems))
      .map(_.toSeq)
      .getOrElse(Seq.empty)

  private def hasNextPage(paging: Paging[?]): Boolean =
    Option(paging.getNext).exists(_.trim.nonEmpty)

  private def nextOffset(paging: Paging[?]): Option[Int] =
    Option.when(hasNextPage(paging))(nextPageOffset(paging))

  private def nextPageOffset(paging: Paging[?]): Int =
    Option(paging.getOffset).map(_.toInt).getOrElse(0) +
      Option(paging.getLimit).map(_.toInt).getOrElse(pageItems(paging).size)

  private def countryCode(value: String): CountryCode =
    Option(CountryCode.getByCodeIgnoreCase(value.trim))
      .getOrElse(throw ClientException.InvalidResponse(new IllegalArgumentException(s"invalid Spotify market: $value")))

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

  private def cursorAfter(paging: PagingCursorbased[Artist]): Option[String] =
    Option(paging.getCursors)
      .flatMap(_.headOption)
      .flatMap(cursor => Option(cursor.getAfter).map(_.trim).filter(_.nonEmpty))

  private def nextUrlAfter(nextUrl: String): Option[String] =
    Try(URI.create(nextUrl.stripSuffix("/")).getRawQuery).toOption
      .flatMap(Option(_))
      .flatMap { rawQuery =>
        rawQuery
          .split("&")
          .iterator
          .flatMap { parameter =>
            parameter.split("=", 2).toList match {
              case key :: value :: Nil if decodeQueryValue(key) == "after" =>
                Option(decodeQueryValue(value).trim).filter(_.nonEmpty)
              case _ =>
                None
            }
          }
          .toSeq
          .headOption
      }

  private def decodeQueryValue(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def imageJson(image: Image): Json =
    Json.obj(
      "url" -> Option(image.getUrl).map(Json.fromString).getOrElse(Json.Null),
      "height" -> Option(image.getHeight).map(value => Json.fromInt(value.toInt)).getOrElse(Json.Null),
      "width" -> Option(image.getWidth).map(value => Json.fromInt(value.toInt)).getOrElse(Json.Null)
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

  private def toMillisInt(value: Long): Integer =
    math.min(value, Int.MaxValue.toLong).toInt

  private def classify(exception: Throwable): SpotifyClientException =
    exception match {
      case e: SpotifyClientException =>
        e
      case e: UnauthorizedException =>
        ClientException.Unauthorized(e)
      case e: ForbiddenException =>
        ClientException.Forbidden(e)
      case e: TooManyRequestsException =>
        ClientException.RateLimited(Option(e.getRetryAfter).filter(_ > 0).map(_.seconds), e)
      case e: InternalServerErrorException =>
        ClientException.ServerError(e)
      case e: BadGatewayException =>
        ClientException.ServerError(e)
      case e: ServiceUnavailableException =>
        ClientException.ServerError(e)
      case e: IOException =>
        ClientException.Network(e)
      case e: ParseException =>
        ClientException.InvalidResponse(e)
      case e: se.michaelthelin.spotify.exceptions.SpotifyWebApiException =>
        ClientException.ClientError(e)
      case NonFatal(e) =>
        ClientException.Unknown(e)
    }
}
