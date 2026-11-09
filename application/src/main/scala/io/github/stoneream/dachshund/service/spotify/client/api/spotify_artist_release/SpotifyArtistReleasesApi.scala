package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import com.google.inject.{Inject, Singleton}
import com.neovisionaries.i18n.CountryCode
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyArtistReleaseSummaryPage}
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestExecutor
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.model_objects.specification.{Album, Paging, TrackSimplified}

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
          builder.build().execute()
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
              detail = detail,
              tracks = tracks
            )
          }
        )
    }

  private def getAlbum(
      spotifyApi: SpotifyApi,
      releaseCode: String,
      market: Option[String]
  )(using LoggingContext): Future[Album] = {
    val builder = spotifyApi.getAlbum(releaseCode)
    market.foreach(value => builder.market(countryCode(value)))

    requestExecutor.executeSdk("api-album-detail") {
      builder.build().execute()
    }
  }

  private def getAlbumTracks(
      spotifyApi: SpotifyApi,
      album: Album,
      market: Option[String]
  )(using LoggingContext): Future[Seq[TrackSimplified]] = {
    val firstPage = Option(album.getTracks)
    val initialTracks = firstPage.map(pageItems).getOrElse(Seq.empty)

    def fetchRemaining(
        currentPage: Paging[TrackSimplified],
        accumulated: Seq[TrackSimplified]
    ): Future[Seq[TrackSimplified]] =
      if (!hasNextPage(currentPage)) {
        Future.successful(accumulated)
      } else {
        val builder = spotifyApi
          .getAlbumsTracks(album.getId)
          .limit(50)
          .offset(nextPageOffset(currentPage))
        market.foreach(value => builder.market(countryCode(value)))

        requestExecutor
          .executeSdk("api-album-tracks") {
            builder.build().execute()
          }
          .flatMap(nextPage => fetchRemaining(nextPage, accumulated ++ pageItems(nextPage)))
      }

    firstPage match {
      case Some(page) => fetchRemaining(page, initialTracks)
      case None => Future.successful(initialTracks)
    }
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
}
