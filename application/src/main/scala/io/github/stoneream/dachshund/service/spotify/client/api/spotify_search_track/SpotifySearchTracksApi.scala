package io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model.{SpotifyTrackSearchItem, SpotifyTrackSearchPage}
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestExecutor
import se.michaelthelin.spotify.model_objects.specification.{Paging, Track}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
private[client] class SpotifySearchTracksApi @Inject() (
    requestExecutor: SpotifyRequestExecutor,
    ioDispatcher: IoDispatcher
) {
  private given ExecutionContext = ioDispatcher

  def searchTracks(
      accessToken: String,
      query: String,
      limit: Int,
      offset: Int
  )(using LoggingContext): Future[SpotifyTrackSearchPage] =
    requestExecutor.recoverFailures {
      val request = requestExecutor
        .spotifyApi(accessToken)
        .searchTracks(query)
        .limit(math.max(1, math.min(limit, SpotifyTrackSearchPage.PageSize)))
        .offset(math.max(0, math.min(offset, SpotifyTrackSearchPage.MaxOffset)))
        .build()

      requestExecutor.executeSdk("api-search-tracks")(request.execute()).map(toPage)
    }

  private def toPage(page: Paging[Track]): SpotifyTrackSearchPage = {
    val offset = page.getOffset.toInt
    val limit = page.getLimit.toInt
    val nextOffset = offset.toLong + limit
    SpotifyTrackSearchPage(
      tracks = Option(page.getItems).toSeq.flatMap(_.toSeq).flatMap(Option(_)).map(toTrack),
      total = page.getTotal.toInt,
      offset = offset,
      limit = limit,
      nextOffset = Option.when(
        Option(page.getNext).exists(_.nonEmpty) && limit > 0 && nextOffset <= SpotifyTrackSearchPage.MaxOffset
      )(nextOffset.toInt)
    )
  }

  private def toTrack(track: Track): SpotifyTrackSearchItem = {
    val album = Option(track.getAlbum)
    SpotifyTrackSearchItem(
      spotifyTrackCode = Option(track.getId).getOrElse(""),
      trackName = Option(track.getName).getOrElse(""),
      artistNames = Option(track.getArtists).toSeq.flatMap(_.toSeq).flatMap(artist => Option(artist).flatMap(a => Option(a.getName))),
      albumName = album.flatMap(a => Option(a.getName)).getOrElse(""),
      imageUrl = album
        .flatMap(a => Option(a.getImages))
        .toSeq
        .flatMap(_.toSeq)
        .flatMap(image => Option(image).flatMap(i => Option(i.getUrl)))
        .find(_.nonEmpty)
    )
  }
}
