package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

private[callback] object NormalizeSpotifyScopeText {
  def apply(scopeText: String): String =
    scopeText
      .split("\\s+")
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct
      .sorted
      .mkString(" ")
}
