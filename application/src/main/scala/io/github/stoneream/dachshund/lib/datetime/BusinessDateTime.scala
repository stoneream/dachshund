package io.github.stoneream.dachshund.lib.datetime

import java.time.{LocalDate, LocalDateTime, OffsetDateTime, ZoneId}
import scala.concurrent.duration.FiniteDuration

final class BusinessDateTime(private val value: OffsetDateTime) {
  def asOffsetDateTime: OffsetDateTime = value

  def toLocalDate: LocalDate = value.toLocalDate

  def toLocalDateTime: LocalDateTime = value.toLocalDateTime

  def isBefore(other: BusinessDateTime): Boolean =
    value.isBefore(other.value)

  def isAfter(other: BusinessDateTime): Boolean =
    value.isAfter(other.value)

  def plus(duration: FiniteDuration): BusinessDateTime =
    new BusinessDateTime(value.plusNanos(duration.toNanos))

  def minus(duration: FiniteDuration): BusinessDateTime =
    new BusinessDateTime(value.minusNanos(duration.toNanos))

  override def equals(obj: Any): Boolean =
    obj match {
      case other: BusinessDateTime => value == other.value
      case _ => false
    }

  override def hashCode(): Int = value.hashCode()

  override def toString: String = value.toString
}

object BusinessDateTime {
  def from(value: OffsetDateTime): BusinessDateTime =
    new BusinessDateTime(value)

  def from(value: String): BusinessDateTime =
    BusinessDateTime.from(OffsetDateTime.parse(value))

  def fromLocalDateTime(value: LocalDateTime): BusinessDateTime =
    BusinessDateTime.from(value.atZone(ZoneId.systemDefault()).toOffsetDateTime)

  final val MAX: BusinessDateTime =
    BusinessDateTime.from("9999-12-31T23:59:59.999999+09:00")
}
