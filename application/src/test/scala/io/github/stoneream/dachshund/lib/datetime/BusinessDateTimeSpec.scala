package io.github.stoneream.dachshund.lib.datetime

import org.scalatest.featurespec.AnyFeatureSpec

import java.time.{LocalDate, LocalDateTime, OffsetDateTime, ZoneId}
import scala.concurrent.duration.*

class BusinessDateTimeSpec extends AnyFeatureSpec {
  Feature("Business date time") {
    Scenario("FiniteDuration を加算した日時を返す") {
      val base = BusinessDateTime.from("2026-06-21T12:00:00+09:00")

      val result = base.plus(90.seconds)

      assert(result.toLocalDateTime == OffsetDateTime.parse("2026-06-21T12:01:30+09:00").toLocalDateTime)
    }

    Scenario("保持している offset の日付を返す") {
      val base = BusinessDateTime.from("2026-06-21T00:30:00+09:00")

      assert(base.toLocalDate == LocalDate.parse("2026-06-21"))
    }

    Scenario("offset 付き日時文字列から生成する") {
      val result = BusinessDateTime.from("2026-06-21T12:00:00+09:00")

      assert(result.asOffsetDateTime == OffsetDateTime.parse("2026-06-21T12:00:00+09:00"))
    }

    Scenario("同じ offset 付き日時なら等価として扱う") {
      val left = BusinessDateTime.from("2026-06-21T12:00:00+09:00")
      val right = BusinessDateTime.from("2026-06-21T12:00:00+09:00")
      val other = BusinessDateTime.from("2026-06-21T12:00:01+09:00")

      assert(left == right)
      assert(left.hashCode() == right.hashCode())
      assert(left != other)
    }

    Scenario("LocalDateTime をシステムデフォルトの offset 付き日時として扱う") {
      val localDateTime = LocalDateTime.parse("2026-06-21T12:00:00")

      val result = BusinessDateTime.fromLocalDateTime(localDateTime)

      assert(result.toLocalDateTime == localDateTime)
      assert(result.asOffsetDateTime == localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime)
    }

    Scenario("保持している offset 付き日時の文字列表現を返す") {
      val offsetDateTime = OffsetDateTime.parse("2026-06-21T12:00:00+09:00")
      val base = BusinessDateTime.from(offsetDateTime)

      assert(base.toString == offsetDateTime.toString)
    }

    Scenario("FiniteDuration を差し引いた日時を返す") {
      val base = BusinessDateTime.from("2026-06-21T12:00:00+09:00")

      val result = base.minus(90.seconds)

      assert(result.toLocalDateTime == OffsetDateTime.parse("2026-06-21T11:58:30+09:00").toLocalDateTime)
    }

    Scenario("他の BusinessDateTime より前後かを判定する") {
      val base = BusinessDateTime.from("2026-06-21T12:00:00+09:00")
      val before = base.minus(1.second)
      val after = base.plus(1.second)

      assert(before.isBefore(base))
      assert(after.isAfter(base))
      assert(!base.isBefore(base))
      assert(!base.isAfter(base))
    }
  }
}
