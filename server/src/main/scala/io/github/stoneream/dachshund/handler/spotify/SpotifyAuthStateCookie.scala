package io.github.stoneream.dachshund.handler.spotify

import play.api.mvc.{Cookie, DiscardingCookie}

object SpotifyAuthStateCookie {
  private val Path = "/spotify/auth"

  def create(name: String, value: String, maxAgeSeconds: Long): Cookie =
    Cookie(
      name = name,
      value = value,
      maxAge = Some(maxAgeSeconds.toInt),
      path = Path,
      secure = false,
      httpOnly = true,
      sameSite = Some(Cookie.SameSite.Lax)
    )

  def discard(name: String): DiscardingCookie =
    DiscardingCookie(
      name = name,
      path = Path,
      secure = false
    )
}
