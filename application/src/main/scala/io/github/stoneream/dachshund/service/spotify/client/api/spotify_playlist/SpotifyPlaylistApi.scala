package io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist

import com.google.inject.{Inject, Singleton}
import io.circe.{Decoder, Json}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.SpotifyPlaylistApi.*
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_playlist.model.{SpotifyAddItemsToPlaylistResult, SpotifyCreatePlaylistResult, SpotifyPlaylist, SpotifyPlaylistPage}
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestExecutor
import sttp.client3.circe.asJson
import sttp.client3.{UriContext, asString, basicRequest}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

@Singleton
private[client] class SpotifyPlaylistApi @Inject() (
    applicationConfig: ApplicationConfig,
    requestExecutor: SpotifyRequestExecutor,
    ioDispatcher: IoDispatcher
) {
  private given ExecutionContext = ioDispatcher
  private val clientConfig = applicationConfig.spotify.client

  def addItemsToPlaylist(
      accessToken: String,
      spotifyPlaylistCode: String,
      trackUris: Seq[String]
  )(using LoggingContext): Future[SpotifyAddItemsToPlaylistResult] = {
    val cleanedTrackUris = trackUris.map(_.trim).filter(_.nonEmpty).distinct

    if (cleanedTrackUris.isEmpty) {
      Future.failed(ClientException.InvalidResponse(new IllegalArgumentException("Spotify playlist add items requires at least one track URI")))
    } else {
      requestExecutor.recoverFailures {
        cleanedTrackUris
          .grouped(PlaylistAddItemsLimit)
          .foldLeft(Future.successful(Option.empty[SpotifyAddItemsToPlaylistResult])) { (futureResult, chunk) =>
            futureResult.flatMap(_ => addItemsToPlaylistChunk(accessToken, spotifyPlaylistCode, chunk))
          }
          .flatMap {
            case Some(result) => Future.successful(result)
            case None => Future.failed(ClientException.InvalidResponse(new IllegalStateException("Spotify playlist add items did not return snapshot id")))
          }
      }
    }
  }

  def getCurrentUserPlaylistPage(
      accessToken: String,
      limit: Int,
      offset: Int
  )(using LoggingContext): Future[SpotifyPlaylistPage] =
    requestExecutor.recoverFailures {
      val endpointName = "api-current-user-playlists"
      val cleanedLimit = math.max(1, math.min(limit, 50))
      val cleanedOffset = math.max(0, offset)
      val endpoint = currentUserPlaylistsEndpoint(cleanedLimit, cleanedOffset)

      requestExecutor
        .executeJson[SpotifyPlaylistPageResponse](endpointName) { backend =>
          basicRequest
            .get(uri"$endpoint")
            .auth
            .bearer(accessToken)
            .readTimeout(clientConfig.requestTimeout)
            .response(asJson[SpotifyPlaylistPageResponse])
            .send(backend)
        }
        .map(toSpotifyPlaylistPage)
    }

  def createCurrentUserPlaylist(
      accessToken: String,
      playlistName: String,
      isPublic: Boolean
  )(using LoggingContext): Future[SpotifyCreatePlaylistResult] =
    requestExecutor.recoverFailures {
      val endpointName = "api-current-user-playlist-create"
      val endpoint = currentUserPlaylistsEndpoint
      val requestBody = Json
        .obj(
          "name" -> Json.fromString(playlistName),
          "public" -> Json.fromBoolean(isPublic)
        )
        .noSpaces

      requestExecutor
        .executeJson[SpotifyCreatePlaylistResponse](endpointName) { backend =>
          basicRequest
            .post(uri"$endpoint")
            .auth
            .bearer(accessToken)
            .contentType("application/json")
            .readTimeout(clientConfig.requestTimeout)
            .body(requestBody)
            .response(asJson[SpotifyCreatePlaylistResponse])
            .send(backend)
        }
        .map(toSpotifyCreatePlaylistResult)
    }

  def unfollowPlaylist(
      accessToken: String,
      spotifyPlaylistCode: String
  )(using LoggingContext): Future[Unit] =
    requestExecutor.recoverFailures {
      val endpointName = "api-playlist-unfollow"
      val endpoint = playlistFollowersEndpoint(spotifyPlaylistCode)

      requestExecutor.executeEmpty(endpointName) { backend =>
        basicRequest
          .delete(uri"$endpoint")
          .auth
          .bearer(accessToken)
          .readTimeout(clientConfig.requestTimeout)
          .response(asString)
          .send(backend)
      }
    }

  private def addItemsToPlaylistChunk(
      accessToken: String,
      spotifyPlaylistCode: String,
      trackUris: Seq[String]
  )(using LoggingContext): Future[Option[SpotifyAddItemsToPlaylistResult]] = {
    val endpointName = "api-playlist-add-items"
    val endpoint = playlistItemsEndpoint(spotifyPlaylistCode)
    val requestBody = Json
      .obj(
        "uris" -> Json.arr(trackUris.map(Json.fromString)*)
      )
      .noSpaces

    requestExecutor
      .executeJson[SpotifyPlaylistSnapshotResponse](endpointName) { backend =>
        basicRequest
          .post(uri"$endpoint")
          .auth
          .bearer(accessToken)
          .contentType("application/json")
          .readTimeout(clientConfig.requestTimeout)
          .body(requestBody)
          .response(asJson[SpotifyPlaylistSnapshotResponse])
          .send(backend)
      }
      .map(response => Some(SpotifyAddItemsToPlaylistResult(response.snapshotId)))
  }

  private def spotifyEndpoint(path: String): String =
    s"${clientConfig.apiBaseUrl.stripSuffix("/")}/${path.stripPrefix("/")}"

  private def encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  private def playlistItemsEndpoint(spotifyPlaylistCode: String): String =
    spotifyEndpoint(s"/playlists/${encode(spotifyPlaylistCode)}/items")

  private def playlistFollowersEndpoint(spotifyPlaylistCode: String): String =
    spotifyEndpoint(s"/playlists/${encode(spotifyPlaylistCode)}/followers")

  private def currentUserPlaylistsEndpoint: String =
    spotifyEndpoint("/me/playlists")

  private def currentUserPlaylistsEndpoint(limit: Int, offset: Int): String =
    s"$currentUserPlaylistsEndpoint?limit=$limit&offset=$offset"

  private def toSpotifyPlaylistPage(response: SpotifyPlaylistPageResponse): SpotifyPlaylistPage =
    SpotifyPlaylistPage(
      playlists = response.items.flatMap(toSpotifyPlaylist),
      nextOffset = response.next.map(_.trim).filter(_.nonEmpty).map(_ => response.offset + response.limit)
    )

  private def toSpotifyPlaylist(response: SpotifyPlaylistResponse): Option[SpotifyPlaylist] =
    response.id.map(_.trim).filter(_.nonEmpty).map { spotifyPlaylistCode =>
      SpotifyPlaylist(
        spotifyPlaylistCode = spotifyPlaylistCode,
        playlistName = response.name.getOrElse(""),
        spotifyPlaylistUri = response.uri.getOrElse("")
      )
    }

  private def toSpotifyCreatePlaylistResult(response: SpotifyCreatePlaylistResponse): SpotifyCreatePlaylistResult =
    Option(response.id).map(_.trim).filter(_.nonEmpty) match {
      case Some(spotifyPlaylistCode) =>
        SpotifyCreatePlaylistResult(
          spotifyPlaylistCode = spotifyPlaylistCode,
          playlistName = response.name,
          spotifyPlaylistUri = response.uri
        )
      case None =>
        throw ClientException.InvalidResponse(new IllegalStateException("Spotify create playlist response did not contain playlist id"))
    }
}

private[spotify_playlist] object SpotifyPlaylistApi {
  private val PlaylistAddItemsLimit = 100

  private final case class SpotifyPlaylistSnapshotResponse(
      snapshotId: String
  )

  private object SpotifyPlaylistSnapshotResponse {
    given Decoder[SpotifyPlaylistSnapshotResponse] =
      Decoder.forProduct1("snapshot_id")(SpotifyPlaylistSnapshotResponse.apply)
  }

  private final case class SpotifyPlaylistPageResponse(
      items: Seq[SpotifyPlaylistResponse],
      next: Option[String],
      limit: Int,
      offset: Int
  )

  private object SpotifyPlaylistPageResponse {
    given Decoder[SpotifyPlaylistPageResponse] =
      Decoder.forProduct4("items", "next", "limit", "offset")(SpotifyPlaylistPageResponse.apply)
  }

  private final case class SpotifyPlaylistResponse(
      id: Option[String],
      name: Option[String],
      uri: Option[String]
  )

  private object SpotifyPlaylistResponse {
    given Decoder[SpotifyPlaylistResponse] =
      Decoder.forProduct3("id", "name", "uri")(SpotifyPlaylistResponse.apply)
  }

  private final case class SpotifyCreatePlaylistResponse(
      id: String,
      name: String,
      uri: String
  )

  private object SpotifyCreatePlaylistResponse {
    given Decoder[SpotifyCreatePlaylistResponse] =
      Decoder.forProduct3("id", "name", "uri")(SpotifyCreatePlaylistResponse.apply)
  }
}
