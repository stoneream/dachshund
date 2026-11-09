package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release

import com.google.inject.{Inject, Singleton}
import io.circe.Decoder
import io.circe.parser.decode
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.SpotifyArtistReleaseResponse.*
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model.{SpotifyArtistRelease, SpotifyArtistReleaseSummary, SpotifyArtistReleaseSummaryPage}
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestExecutor
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.enums.CountryCode

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
          decodeResponse[PageResponse[AlbumResponse]](builder.build().getJson())
        }
        .map { page =>
          SpotifyArtistReleaseSummaryPage(
            releases = page.values.flatMap(SpotifyArtistReleaseMapper.toSummary),
            nextOffset = page.nextOffset
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

      for {
        detail <- getAlbum(spotifyApi, summary.spotifyReleaseCode, market)
        tracks <- getAlbumTracks(spotifyApi, detail, market)
      } yield SpotifyArtistReleaseMapper.toRelease(sourceSpotifyArtistCode, summary, detail, tracks)
    }

  private def getAlbum(
      spotifyApi: SpotifyApi,
      releaseCode: String,
      market: Option[String]
  )(using LoggingContext): Future[AlbumResponse] = {
    val builder = spotifyApi.getAlbum(releaseCode)
    market.foreach(value => builder.market(countryCode(value)))

    requestExecutor.executeSdk("api-album-detail") {
      decodeResponse[AlbumResponse](builder.build().getJson())
    }
  }

  private def getAlbumTracks(
      spotifyApi: SpotifyApi,
      album: AlbumResponse,
      market: Option[String]
  )(using LoggingContext): Future[Seq[TrackResponse]] = {
    def fetchRemaining(page: PageResponse[TrackResponse], accumulated: Seq[TrackResponse]): Future[Seq[TrackResponse]] =
      page.nextOffset match {
        case None => Future.successful(accumulated)
        case Some(offset) =>
          val releaseCode = album.id.getOrElse {
            throw ClientException.InvalidResponse(new IllegalStateException("Spotify album response is missing its id"))
          }
          val builder = spotifyApi.getAlbumTracks(releaseCode).limit(50).offset(offset)
          market.foreach(value => builder.market(countryCode(value)))

          requestExecutor
            .executeSdk("api-album-tracks") {
              decodeResponse[PageResponse[TrackResponse]](builder.build().getJson())
            }
            .flatMap(nextPage => fetchRemaining(nextPage, accumulated ++ nextPage.values))
      }

    album.tracks.fold(Future.successful(Seq.empty[TrackResponse]))(page => fetchRemaining(page, page.values))
  }

  private def decodeResponse[A: Decoder](json: String): A =
    decode[A](json).fold(error => throw ClientException.InvalidResponse(error), identity)

  private def countryCode(value: String): CountryCode =
    Option(CountryCode.getByCode(value.trim))
      .getOrElse(throw ClientException.InvalidResponse(new IllegalArgumentException(s"invalid Spotify market: $value")))
}
