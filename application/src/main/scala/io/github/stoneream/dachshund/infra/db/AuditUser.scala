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
}
