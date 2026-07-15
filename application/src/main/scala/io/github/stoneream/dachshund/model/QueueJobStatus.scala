package io.github.stoneream.dachshund.model

enum QueueJobStatus(val dbValue: String) {
  case Scheduled extends QueueJobStatus("SCHEDULED")
  case Processing extends QueueJobStatus("PROCESSING")
  case Succeeded extends QueueJobStatus("SUCCEEDED")
  case Failed extends QueueJobStatus("FAILED")
  case Blocked extends QueueJobStatus("BLOCKED")
  case Skipped extends QueueJobStatus("SKIPPED")
}

object QueueJobStatus {
  def fromDbValue(value: String): QueueJobStatus =
    values
      .find(_.dbValue == value)
      .getOrElse(throw IllegalArgumentException(s"キュージョブステータスが想定外です: $value"))
}
