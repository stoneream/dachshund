package io.github.stoneream.dachshund.service.spotify.auth.access_token.step

/** Spotify スコープ文字列を安定した空白区切り形式に正規化する。 */
private[auth] object NormalizeSpotifyScopeText {
  def apply(scopeText: String): String =
    scopeText
      .split("\\s+")
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct
      .sorted
      .mkString(" ")
}
