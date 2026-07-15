package io.github.stoneream.dachshund.usecase.home

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.infra.db.reader.home.HomeNewReleaseReader
import io.github.stoneream.dachshund.infra.db.transaction.{DatabaseRole, DatabaseTransaction}
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase
import io.github.stoneream.dachshund.usecase.home.{HomeUseCaseException => UseCaseException, HomeUseCaseInput => UseCaseInput, HomeUseCaseOutput => UseCaseOutput}
import io.github.stoneream.dachshund.usecase.home.HomeUseCaseOutput.{HomeUser, NewRelease, NewReleaseDay, NewReleaseMonth}

import java.time.{LocalTime, YearMonth}
import scala.concurrent.duration.*
import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class HomeUseCase @Inject() (
    databaseTransaction: DatabaseTransaction,
    homeNewReleaseReader: HomeNewReleaseReader,
    databaseExecutor: DatabaseExecutor
) extends UseCase[
      UseCaseInput,
      UseCaseOutput,
      UseCaseException
    ]
    with TraceLogger {

  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
    info("home ユースケースを実行します", kv("input", input))
    input.userSessionContext match {
      case UserSessionContext.NotLoggedIn =>
        Future.successful(
          UseCaseOutput(
            now = input.now,
            user = None,
            newReleaseMonths = Seq.empty
          )
        )
      case user: UserSessionContext.NormalUser =>
        Future {
          val releasedTo = input.now.toLocalDate.atTime(LocalTime.MAX)
          val releasedFrom = input.now.minus(30.days).toLocalDate.atStartOfDay()
          val releases = databaseTransaction.readOnly(DatabaseRole.Master) { implicit session =>
            homeNewReleaseReader.findRecentReleases(
              userId = user.userId,
              releasedFrom = releasedFrom,
              releasedTo = releasedTo,
              limit = 200
            )
          }
          val newReleaseDays = releases
            .groupBy(_.releaseDate)
            .toSeq
            .sortWith { case ((leftReleaseDate, _), (rightReleaseDate, _)) =>
              leftReleaseDate.isAfter(rightReleaseDate)
            }
            .map { case (releaseDate, dayReleases) =>
              NewReleaseDay(
                releaseDate = releaseDate,
                releases = dayReleases.map { release =>
                  NewRelease(
                    artistReleaseId = release.artistReleaseId,
                    releaseName = release.releaseName,
                    releaseType = release.releaseType,
                    labelName = release.labelName,
                    spotifyUrl = release.spotifyUrl,
                    primaryImageUrl = release.primaryImageUrl,
                    sourceArtistName = release.sourceArtistName
                  )
                }
              )
            }
          UseCaseOutput(
            now = input.now,
            user = Some(HomeUser(userId = user.userId, displayName = user.displayName)),
            newReleaseMonths = newReleaseDays
              .groupBy(day => YearMonth.from(day.releaseDate))
              .toSeq
              .sortWith { case ((leftMonth, _), (rightMonth, _)) =>
                leftMonth.isAfter(rightMonth)
              }
              .map { case (releaseMonth, releaseDays) =>
                NewReleaseMonth(
                  releaseMonth = releaseMonth,
                  releaseDays = releaseDays.sortWith((leftDay, rightDay) => leftDay.releaseDate.isAfter(rightDay.releaseDate))
                )
              }
          )
        }(using databaseExecutor)
    }
  }
}
