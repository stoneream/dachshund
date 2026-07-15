package io.github.stoneream.dachshund.daemon.config

import org.scalatest.featurespec.AnyFeatureSpec

import java.time.{LocalDate, LocalTime, ZoneId, ZonedDateTime}
import scala.concurrent.duration.*

class JobScheduleSpec extends AnyFeatureSpec {
  Feature("job schedule") {
    Scenario("Every は現在時刻に関係なく interval を次回 delay にする") {
      val now = ZonedDateTime.parse("2026-06-20T12:00:00+09:00[Asia/Tokyo]")

      val delay = JobSchedule.Every(2.days).nextDelay(now)

      assert(delay == 2.days)
    }

    Scenario("Every の interval が 0 の場合は validation で拒否する") {
      val result = JobSchedule.every(0.seconds, "daemon.jobs.test.interval")

      assert(result.isLeft)
    }

    Scenario("DailyAt は当日の指定時刻が未来なら当日までの delay を返す") {
      val now = ZonedDateTime.parse("2026-06-20T12:00:00+09:00[Asia/Tokyo]")

      val delay = JobSchedule.DailyAt(LocalTime.of(18, 30)).nextDelay(now)

      assert(delay == 6.hours + 30.minutes)
    }

    Scenario("DailyAt は当日の指定時刻を過ぎていれば翌日までの delay を返す") {
      val now = ZonedDateTime.parse("2026-06-20T18:31:00+09:00[Asia/Tokyo]")

      val delay = JobSchedule.DailyAt(LocalTime.of(18, 30)).nextDelay(now)

      assert(delay == 23.hours + 59.minutes)
    }

    Scenario("EveryDaysAt は start date から n 日ごとの指定日時までの delay を返す") {
      val now = ZonedDateTime.parse("2026-06-04T10:00:00+09:00[Asia/Tokyo]")
      val schedule = JobSchedule.EveryDaysAt(
        days = 3,
        time = LocalTime.of(9, 0),
        startDate = LocalDate.parse("2026-06-01"),
        zoneId = ZoneId.of("Asia/Tokyo")
      )

      val delay = schedule.nextDelay(now)

      assert(delay == 2.days + 23.hours)
    }
  }
}
