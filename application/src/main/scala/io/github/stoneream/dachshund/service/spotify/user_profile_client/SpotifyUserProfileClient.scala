package io.github.stoneream.dachshund.service.spotify.user_profile_client

import io.circe.Decoder
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext

import scala.concurrent.Future

trait SpotifyUserProfileClient {
  import SpotifyUserProfileClient.*

  def getCurrentUserProfile(accessToken: String)(using LoggingContext): Future[CurrentUserProfile]
}

object SpotifyUserProfileClient {
  final case class CurrentUserProfile(
      id: String,
      displayName: Option[String]
  )

  object CurrentUserProfile {
    given Decoder[CurrentUserProfile] =
      Decoder.forProduct2(
        "id",
        "display_name"
      )(CurrentUserProfile.apply)
  }

  final case class ErrorBody(
      status: Option[Int],
      message: Option[String]
  )

  object ErrorBody {
    given Decoder[ErrorBody] =
      Decoder.forProduct2("status", "message")(ErrorBody.apply)
  }

  final case class ErrorResponse(
      error: Option[ErrorBody]
  )

  object ErrorResponse {
    given Decoder[ErrorResponse] =
      Decoder.forProduct1("error")(ErrorResponse.apply)
  }
}
