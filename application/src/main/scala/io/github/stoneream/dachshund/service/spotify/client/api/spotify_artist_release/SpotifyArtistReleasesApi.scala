package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import com.google.gson.{JsonObject, JsonParser}
import com.google.inject.{Inject, Singleton}
import se.michaelthelin.spotify.enums.CountryCode
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyArtistReleaseSummaryPage}
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestExecutor
import se.michaelthelin.spotify.SpotifyApi

import scala.jdk.CollectionConverters.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
private[client] class SpotifyArtistReleasesApi @Inject() (
    requestExecutor: SpotifyRequestExecutor,
    ioDispatcher: IoDispatcher
) {
  private given ExecutionContext = ioDispatcher

  def getArtistReleaseSummaryPage(
      accessToken: String,
      spotifyArtistCode: String,
      includeGroups: String,
      market: Option[String],
      limit: Int,
      offset: Int
  )(using LoggingContext): Future[SpotifyArtistReleaseSummaryPage] =
    requestExecutor.recoverFailures {
      val builder = requestExecutor
        .spotifyApi(accessToken)
        .getArtistsAlbums(spotifyArtistCode)
        .include_groups(includeGroups)
        .limit(limit)
        .offset(offset)

      market.foreach(value => builder.market(countryCode(value)))

      requestExecutor
        .executeSdk("api-artist-albums") {
          JsonParser.parseString(builder.build().getJson()).getAsJsonObject
        }
        .map { page =>
          SpotifyArtistReleaseSummaryPage(
            releases = pageItems(page).flatMap(SpotifyArtistReleaseMapper.toSummary),
            nextOffset = nextOffset(page)
          )
        }
    }

  def getArtistRelease(
      accessToken: String,
      sourceSpotifyArtistCode: String,
      summary: SpotifyArtistReleaseSummary,
      market: Option[String]
  )(using LoggingContext): Future[SpotifyArtistRelease] =
    requestExecutor.recoverFailures {
      val spotifyApi = requestExecutor.spotifyApi(accessToken)

      getAlbum(spotifyApi, summary.spotifyReleaseCode, market)
        .flatMap(detail =>
          getAlbumTracks(spotifyApi, detail, market).map { tracks =>
            SpotifyArtistReleaseMapper.toRelease(
              sourceSpotifyArtistCode = sourceSpotifyArtistCode,
              summary = summary,
              detailJson = detail,
              tracks = tracks
            )
          }
        )
    }

  private def getAlbum(
      spotifyApi: SpotifyApi,
      releaseCode: String,
      market: Option[String]
  )(using LoggingContext): Future[JsonObject] = {
    val builder = spotifyApi.getAlbum(releaseCode)
    market.foreach(value => builder.market(countryCode(value)))

    requestExecutor.executeSdk("api-album-detail") {
      JsonParser.parseString(builder.build().getJson()).getAsJsonObject
    }
  }

  private def getAlbumTracks(
      spotifyApi: SpotifyApi,
      album: JsonObject,
      market: Option[String]
  )(using LoggingContext): Future[Seq[JsonObject]] = {
    val firstPage = field(album, "tracks").map(_.getAsJsonObject)
    val initialTracks = firstPage.map(pageItems).getOrElse(Seq.empty)

    def fetchRemaining(
        currentPage: JsonObject,
        accumulated: Seq[JsonObject]
    ): Future[Seq[JsonObject]] =
      if (!hasNextPage(currentPage)) {
        Future.successful(accumulated)
      } else {
        val builder = spotifyApi
          .getAlbumTracks(album.get("id").getAsString)
          .limit(50)
          .offset(nextPageOffset(currentPage))
        market.foreach(value => builder.market(countryCode(value)))

        requestExecutor
          .executeSdk("api-album-tracks") {
            JsonParser.parseString(builder.build().getJson()).getAsJsonObject
          }
          .flatMap(nextPage => fetchRemaining(nextPage, accumulated ++ pageItems(nextPage)))
      }

    firstPage match {
      case Some(page) => fetchRemaining(page, initialTracks)
      case None => Future.successful(initialTracks)
    }
  }

  private def field(json: JsonObject, name: String): Option[com.google.gson.JsonElement] =
    Option(json.get(name)).filterNot(_.isJsonNull)

  private def pageItems(paging: JsonObject): Seq[JsonObject] =
    field(paging, "items").map(_.getAsJsonArray.asScala.toSeq.map(_.getAsJsonObject)).getOrElse(Seq.empty)

  private def hasNextPage(paging: JsonObject): Boolean =
    field(paging, "next").exists(_.getAsString.trim.nonEmpty)

  private def nextOffset(paging: JsonObject): Option[Int] =
    Option.when(hasNextPage(paging))(nextPageOffset(paging))

  private def nextPageOffset(paging: JsonObject): Int =
    field(paging, "offset").map(_.getAsInt).getOrElse(0) +
      field(paging, "limit").map(_.getAsInt).getOrElse(pageItems(paging).size)

  private def countryCode(value: String): CountryCode =
    Option(CountryCode.getByCode(value.trim))
      .getOrElse(throw ClientException.InvalidResponse(new IllegalArgumentException(s"invalid Spotify market: $value")))
}
