package io.github.stoneream.dachshund.service.reccobeats

import com.google.inject.{Inject, Singleton}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.executor.Executors.IoDispatcher
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.service.reccobeats.model.ReccoBeatsAudioFeatures
import sttp.client3.{HttpClientFutureBackend, Response, SttpBackendOptions, UriContext, asStringAlways, basicRequest}

import java.time.{Instant, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class ReccoBeatsClient @Inject() (
    applicationConfig: ApplicationConfig,
    ioDispatcher: IoDispatcher
) extends TraceLogger {
  import ReccoBeatsClient.*

  private given ExecutionContext = ioDispatcher
  private val config = applicationConfig.reccobeats
  private val backend = HttpClientFutureBackend(options = SttpBackendOptions.connectionTimeout(config.connectTimeout))

  def getTrackAudioFeatures(spotifyTrackId: String)(using LoggingContext): Future[Option[ReccoBeatsAudioFeatures]] = {
    if (!spotifyTrackId.matches("[A-Za-z0-9]{22}")) {
      return Future.failed(new IllegalArgumentException("Invalid Spotify track ID"))
    }
    val endpoint = s"${config.apiBaseUrl.stripSuffix("/")}/audio-features"
    basicRequest
      .get(uri"$endpoint?ids=$spotifyTrackId")
      .header("Accept", "application/json")
      .followRedirects(false)
      .readTimeout(config.requestTimeout)
      .response(asStringAlways)
      .send(backend)
      .map { response =>
        info("ReccoBeats 音響指標リクエストが完了しました", kv("statusCode", response.code.code))
        handleResponse(response, spotifyTrackId)
      }
      .recoverWith {
        case exception: ReccoBeatsClientException => Future.failed(exception)
        case NonFatal(exception) => Future.failed(ReccoBeatsClientException.Network(exception))
      }
  }

  private def handleResponse(response: Response[String], spotifyTrackId: String): Option[ReccoBeatsAudioFeatures] =
    response.code.code match {
      case 200 =>
        val result = decode[AudioFeaturesResponse](response.body).fold(error => throw ReccoBeatsClientException.InvalidResponse(error), identity)
        result.content match {
          case Nil => None
          case entry :: Nil if entry.href == s"https://open.spotify.com/track/$spotifyTrackId" => Some(entry.toFeatures)
          case _ => throw ReccoBeatsClientException.InvalidResponse(new IllegalStateException("Unexpected audio features track"))
        }
      case 404 => None
      case 401 | 403 => throw ReccoBeatsClientException.AccessDenied(response.code.code)
      case 429 => throw ReccoBeatsClientException.RateLimited(response.header("Retry-After").flatMap(parseRetryAfter))
      case status => throw ReccoBeatsClientException.HttpError(status)
    }

  private def parseRetryAfter(value: String): Option[FiniteDuration] = {
    val trimmed = value.trim
    trimmed.toLongOption.filter(seconds => seconds >= 0 && seconds <= Long.MaxValue / 1000000000L).map(_.seconds).orElse {
      Try(ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant).toOption.flatMap { until =>
        Try(java.time.Duration.between(Instant.now(), until).toMillis).toOption
          .filter(_ <= Long.MaxValue / 1000000L)
          .map(millis => math.max(0L, millis).millis)
      }
    }
  }
}

object ReccoBeatsClient {
  private given Decoder[Int] = Decoder.decodeJson.emap(json => json.asNumber.flatMap(_.toInt).toRight("Expected an integer JSON number"))
  private given Decoder[Double] = Decoder.decodeJson.emap(json => json.asNumber.map(_.toDouble).toRight("Expected a JSON number"))

  private final case class AudioFeaturesResponse(content: List[AudioFeaturesEntry])
  private given Decoder[AudioFeaturesResponse] = deriveDecoder

  private final case class AudioFeaturesEntry(
      href: String,
      key: Option[Int],
      mode: Option[Int],
      tempo: Option[Double],
      energy: Option[Double],
      danceability: Option[Double],
      valence: Option[Double],
      acousticness: Option[Double],
      instrumentalness: Option[Double],
      liveness: Option[Double],
      speechiness: Option[Double]
  ) {
    def toFeatures: ReccoBeatsAudioFeatures = ReccoBeatsAudioFeatures(
      key = key.filter(value => value >= 0 && value <= 11),
      mode = mode.filter(value => value == 0 || value == 1),
      tempo = tempo.filter(value => value.isFinite && value > 0),
      energy = score(energy),
      danceability = score(danceability),
      valence = score(valence),
      acousticness = score(acousticness),
      instrumentalness = score(instrumentalness),
      liveness = score(liveness),
      speechiness = score(speechiness)
    )

    private def score(value: Option[Double]): Option[Double] = value.filter(number => number.isFinite && number >= 0 && number <= 1)
  }
  private given Decoder[AudioFeaturesEntry] = deriveDecoder
}
