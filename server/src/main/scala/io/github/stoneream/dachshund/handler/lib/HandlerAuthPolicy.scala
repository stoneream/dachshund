package io.github.stoneream.dachshund.handler.lib

sealed trait HandlerAuthPolicy

object HandlerAuthPolicy {
  case object Public extends HandlerAuthPolicy
  case object LoginRequired extends HandlerAuthPolicy
}
