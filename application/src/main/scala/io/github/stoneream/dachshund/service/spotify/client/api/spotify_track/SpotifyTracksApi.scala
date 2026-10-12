package io.github.stoneream.dachshund.service.spotify.client.api.spotify_track

import com.google.inject.{Inject, Singleton}
import com.google.gson.JsonParseException
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_track.model.SpotifyTrackDetail
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestExecutor
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException
import se.michaelthelin.spotify.model_objects.specification.Track

import scala.concurrent.{ExecutionContext, Future}

@Singleton
private[client] class SpotifyTracksApi @Inject() (
    requestExecutor: SpotifyRequestExecutor,
    ioDispatcher: IoDispatcher
) {
  private given ExecutionContext = ioDispatcher

  def getTrack(accessToken: String, trackId: String)(using LoggingContext): Future[Option[SpotifyTrackDetail]] =
    optionalResponse {
      requestExecutor
        .executeSdk("api-track-detail") {
          requestExecutor.spotifyApi(accessToken).getTrack(trackId).build().execute()
        }
        .map(toTrack)
    }

  private def optionalResponse[A](result: Future[A]): Future[Option[A]] =
    requestExecutor.recoverFailures {
      result.map(Some(_)).recover {
        case _: NotFoundException => None
        case exception: JsonParseException => throw SpotifyClientException.InvalidResponse(exception)
        case exception: IllegalStateException => throw SpotifyClientException.InvalidResponse(exception)
        case exception: NumberFormatException => throw SpotifyClientException.InvalidResponse(exception)
        case exception: ClassCastException => throw SpotifyClientException.InvalidResponse(exception)
      }
    }

  private def toTrack(track: Track): SpotifyTrackDetail = {
    if (track == null) throw SpotifyClientException.InvalidResponse(new IllegalStateException("Spotify track response is null"))
    val id = Option(track.getId).filter(_.matches("[A-Za-z0-9]{22}")).getOrElse {
      throw SpotifyClientException.InvalidResponse(new IllegalStateException("Spotify track ID is missing or invalid"))
    }
    val album = Option(track.getAlbum)
    SpotifyTrackDetail(
      spotifyTrackCode = id,
      trackName = Option(track.getName).getOrElse(""),
      artistNames = Option(track.getArtists).toSeq.flatMap(_.toSeq).flatMap(artist => Option(artist).flatMap(a => Option(a.getName))),
      albumName = album.flatMap(a => Option(a.getName)).getOrElse(""),
      imageUrl = album
        .flatMap(a => Option(a.getImages))
        .toSeq
        .flatMap(_.toSeq)
        .flatMap(image => Option(image).flatMap(i => Option(i.getUrl)))
        .find(_.nonEmpty),
      durationMs = Option(track.getDurationMs).map(_.toInt).filter(_ >= 0),
      popularity = Option(track.getPopularity).map(_.toInt).filter(value => value >= 0 && value <= 100)
    )
  }

}
