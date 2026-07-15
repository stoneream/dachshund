package io.github.stoneream.dachshund.model

enum ExternalAuthFlowType(val dbValue: String) {
  case Signup extends ExternalAuthFlowType("SIGNUP")
}

object ExternalAuthFlowType {
  def fromDbValue(dbValue: String): ExternalAuthFlowType =
    values
      .find(_.dbValue == dbValue)
      .getOrElse(throw IllegalArgumentException(s"外部認証フロー種別が想定外です: $dbValue"))
}
