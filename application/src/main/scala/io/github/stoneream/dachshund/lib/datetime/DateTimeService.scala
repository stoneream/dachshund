package io.github.stoneream.dachshund.lib.datetime

import java.time.OffsetDateTime
import com.google.inject.{Inject, Singleton}

@Singleton
class DateTimeService @Inject() () {
  def now(): BusinessDateTime =
    BusinessDateTime.from(
      OffsetDateTime.now(BusinessDateTime.BusinessZoneId)
    )
}
