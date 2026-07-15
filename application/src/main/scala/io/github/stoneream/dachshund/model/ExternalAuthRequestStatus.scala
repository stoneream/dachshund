package io.github.stoneream.dachshund.model

enum ExternalAuthRequestStatus(val dbValue: String) {
  case Pending extends ExternalAuthRequestStatus("PENDING")
  case Processing extends ExternalAuthRequestStatus("PROCESSING")
  case Succeeded extends ExternalAuthRequestStatus("SUCCEEDED")
  case Failed extends ExternalAuthRequestStatus("FAILED")
}

object ExternalAuthRequestStatus {
  def fromDbValue(value: String): ExternalAuthRequestStatus =
    values
      .find(_.dbValue == value)
      .getOrElse(throw IllegalArgumentException(s"外部認証リクエストステータスが想定外です: $value"))
}
