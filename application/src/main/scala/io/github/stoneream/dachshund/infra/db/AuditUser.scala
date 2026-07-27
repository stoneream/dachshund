package io.github.stoneream.dachshund.infra.db

enum AuditUser {
  case Empty
  case System
  case User(userId: Long)

  def dbValue: String =
    this match {
      case Empty => ""
      case System => "system"
      case User(userId) => s"user:$userId"
    }
}

object AuditUser {
  val EmptyDeletedUser: String = Empty.dbValue

  def fromDbValue(value: String): AuditUser =
    value match {
      case "" => Empty
      case "system" => System
      case userValue if userValue.startsWith("user:") =>
        userValue.stripPrefix("user:").toLongOption.map(User.apply).getOrElse {
          throw new IllegalArgumentException(s"audit user が想定外です: $value")
        }
      case _ => throw new IllegalArgumentException(s"audit user が想定外です: $value")
    }
}
