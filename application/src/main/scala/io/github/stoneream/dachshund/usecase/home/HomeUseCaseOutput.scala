package io.github.stoneream.dachshund.usecase.home

import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

import java.time.{LocalDate, YearMonth}

final case class HomeUseCaseOutput(
    now: BusinessDateTime,
    user: Option[HomeUseCaseOutput.HomeUser],
    newReleaseMonths: Seq[HomeUseCaseOutput.NewReleaseMonth]
)

object HomeUseCaseOutput {
  final case class HomeUser(
      userId: Long,
      displayName: String
  )

  final case class NewReleaseMonth(
      releaseMonth: YearMonth,
      releaseDays: Seq[NewReleaseDay]
  )

  final case class NewReleaseDay(
      releaseDate: LocalDate,
      releases: Seq[NewRelease]
  )

  final case class NewRelease(
      artistReleaseId: Long,
      releaseName: String,
      releaseType: String,
      labelName: Option[String],
      spotifyUrl: String,
      primaryImageUrl: String,
      sourceArtistName: String
  )
}
