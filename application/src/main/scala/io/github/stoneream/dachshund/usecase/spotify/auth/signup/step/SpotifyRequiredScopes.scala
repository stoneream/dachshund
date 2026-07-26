package io.github.stoneream.dachshund.usecase.spotify.auth.signup.step

/**
 * https://developer.spotify.com/documentation/web-api/concepts/scopes
 */
private[signup] object SpotifyRequiredScopes {
  val ScopeText: String = Seq(
    "playlist-modify-private",
    "playlist-modify-public",
    "playlist-read-private",
    "user-follow-read"
  ).distinct.sorted.mkString(" ")
}
