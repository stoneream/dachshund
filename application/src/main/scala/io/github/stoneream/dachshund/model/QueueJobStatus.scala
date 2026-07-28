package io.github.stoneream.dachshund.model

import java.util.Locale

enum QueueJobStatus(val dbValue: String) {
  case Scheduled extends QueueJobStatus("SCHEDULED")
  case Processing extends QueueJobStatus("PROCESSING")
  case Succeeded extends QueueJobStatus("SUCCEEDED")
  case Failed extends QueueJobStatus("FAILED")
  case Blocked extends QueueJobStatus("BLOCKED")
  case Skipped extends QueueJobStatus("SKIPPED")
}

object QueueJobStatus {
  private lazy val valuesByDbValue: Map[String, QueueJobStatus] =
    values.map(status => status.dbValue -> status).toMap

  def fromString(value: String): Option[QueueJobStatus] =
    values.find(_.toString.toLowerCase(Locale.ROOT) == value)

  def fromDbValue(value: String): QueueJobStatus =
    valuesByDbValue
      .get(value)
      .getOrElse(throw IllegalArgumentException(s"キュージョブステータスが想定外です: $value"))
}
