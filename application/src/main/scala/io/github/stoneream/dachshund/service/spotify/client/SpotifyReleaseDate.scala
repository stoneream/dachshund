package io.github.stoneream.dachshund.service.spotify.client

import java.time.{LocalDate, LocalDateTime}
import scala.util.Try

private[client] object SpotifyReleaseDate {
  private val UnknownReleaseDate: LocalDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59)

  def releaseDateAt(
      releaseDateText: String,
      releaseDatePrecision: String
  ): Option[LocalDateTime] = {
    val dateText = releaseDateText.trim
    val precision = releaseDatePrecision.trim

    val releaseDate =
      Option
        .when(precision == "day" && dateText.nonEmpty)(dateText)
        .flatMap(value => Try(LocalDate.parse(value).atStartOfDay()).toOption)
        .getOrElse(UnknownReleaseDate)

    Some(releaseDate)
  }
}
