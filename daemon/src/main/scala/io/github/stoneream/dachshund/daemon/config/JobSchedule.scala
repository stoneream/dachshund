package io.github.stoneream.dachshund.daemon.config

import pureconfig.error.CannotConvert

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZonedDateTime}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

sealed trait JobSchedule {
  def nextDelay(now: ZonedDateTime): FiniteDuration
}

object JobSchedule {
  private val DefaultZoneId: ZoneId = ZoneId.of("Asia/Tokyo")

  def every(interval: FiniteDuration, path: String): Either[CannotConvert, JobSchedule] =
    DaemonConfigValidation.positiveDuration(path, interval).map(Every(_))

  final case class Every(interval: FiniteDuration) extends JobSchedule {
    override def nextDelay(now: ZonedDateTime): FiniteDuration = {
      val _ = now
      interval
    }
  }

  final case class DailyAt(
      time: LocalTime,
      zoneId: ZoneId = DefaultZoneId
  ) extends JobSchedule {
    override def nextDelay(now: ZonedDateTime): FiniteDuration =
      delayToNextDateTime(
        now = now,
        nextDateTime = nextDailyDateTime(now.withZoneSameInstant(zoneId), time)
      )
  }

  final case class EveryDaysAt(
      days: Int,
      time: LocalTime,
      startDate: LocalDate,
      zoneId: ZoneId = DefaultZoneId
  ) extends JobSchedule {
    override def nextDelay(now: ZonedDateTime): FiniteDuration = {
      val zonedNow = now.withZoneSameInstant(zoneId)
      val daysFromStart = math.max(0L, ChronoUnit.DAYS.between(startDate, zonedNow.toLocalDate))
      val periods = Math.floorDiv(daysFromStart, days.toLong)
      val candidateDate = startDate.plusDays(periods * days.toLong)
      val candidate = LocalDateTime.of(candidateDate, time).atZone(zoneId)
      val next =
        if (candidate.isAfter(zonedNow)) candidate
        else candidate.plusDays(days.toLong)

      delayToNextDateTime(now = now, nextDateTime = next)
    }
  }

  private def nextDailyDateTime(now: ZonedDateTime, time: LocalTime): ZonedDateTime = {
    val candidate = LocalDateTime.of(now.toLocalDate, time).atZone(now.getZone)

    if (candidate.isAfter(now)) candidate
    else candidate.plusDays(1)
  }

  private def delayToNextDateTime(now: ZonedDateTime, nextDateTime: ZonedDateTime): FiniteDuration = {
    val delayNanos = ChronoUnit.NANOS.between(now.toInstant, nextDateTime.toInstant)

    if (delayNanos < 0L) Duration.Zero
    else FiniteDuration(delayNanos, TimeUnit.NANOSECONDS)
  }

}
