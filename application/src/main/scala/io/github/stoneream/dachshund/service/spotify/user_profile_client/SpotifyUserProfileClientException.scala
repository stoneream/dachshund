package io.github.stoneream.dachshund.service.spotify.user_profile_client

import SpotifyUserProfileClientException.SpotifyApiClientError

abstract sealed class SpotifyUserProfileClientException(
    override val getMessage: String,
    val error: SpotifyApiClientError
) extends RuntimeException(getMessage) {
  val endpoint: String = error.endpoint
  val statusCode: Int = error.statusCode
  val errorCode: Option[String] = error.errorCode
  val errorDescription: Option[String] = error.errorDescription
}

object SpotifyUserProfileClientException {
  final case class SpotifyApiClientError(
      endpoint: String,
      statusCode: Int,
      errorCode: Option[String],
      errorDescription: Option[String]
  )

  final case class ProfileFetchFailed(
      override val error: SpotifyApiClientError
  ) extends SpotifyUserProfileClientException(
        "Spotify ユーザープロフィール取得に失敗しました",
        error
      )
}
