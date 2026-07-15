package io.github.stoneream.dachshund.usecase.spotify.auth.signup.step

/**
 * https://developer.spotify.com/documentation/web-api/concepts/scopes
 */
private[signup] object SpotifyRequiredScopes {
  val ScopeText: String = Seq(
    "user-follow-read"
  ).distinct.sorted.mkString(" ")
}
