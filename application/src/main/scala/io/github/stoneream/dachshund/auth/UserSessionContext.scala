package io.github.stoneream.dachshund.auth

sealed trait UserSessionContext

object UserSessionContext {
  case object NotLoggedIn extends UserSessionContext

  final case class NormalUser(
      userId: Long,
      userName: String,
      displayName: String
  ) extends UserSessionContext
}
