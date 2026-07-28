package io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist

import com.google.inject.{Inject, Singleton}
import io.circe.Json
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.service.spotify.client.SpotifyClientException as ClientException
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_followed_artist.model.{SpotifyFollowedArtist, SpotifyFollowedArtistsPage}
import io.github.stoneream.dachshund.service.spotify.client.lib.SpotifyRequestExecutor
import se.michaelthelin.spotify.enums.ModelObjectType
import se.michaelthelin.spotify.model_objects.specification.{Artist, Image, PagingCursorbased}

import java.net.{URI, URLDecoder}
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
private[client] class SpotifyFollowedArtistsApi @Inject() (
    requestExecutor: SpotifyRequestExecutor,
    ioDispatcher: IoDispatcher
) {
  private given ExecutionContext = ioDispatcher

  def getFollowedArtists(
      accessToken: String,
      afterCursor: Option[String],
      limit: Int
  )(using LoggingContext): Future[SpotifyFollowedArtistsPage] =
    requestExecutor.recoverFailures {
      val builder = requestExecutor
        .spotifyApi(accessToken)
        .getUsersFollowedArtists(ModelObjectType.ARTIST)
        .limit(limit)

      afterCursor.foreach(builder.after)

      requestExecutor
        .executeSdk("api-followed-artists") {
          builder.build().execute()
        }
        .map(toFollowedArtistsPage)
    }

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

  private def nonEmptyJsonArray(values: Seq[Json]): Option[String] =
    Option.when(values.nonEmpty)(Json.arr(values*).noSpaces)
}
