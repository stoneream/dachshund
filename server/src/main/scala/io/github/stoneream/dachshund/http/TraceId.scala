package io.github.stoneream.dachshund.http

import play.api.libs.typedmap.TypedKey

import java.util.UUID

object TraceId {
  val Attr: TypedKey[String] = TypedKey[String]("traceId")
  val Undefined: String = "undefined-trace-id"

  def generate(): String =
    UUID.randomUUID().toString.replace("-", "")
}
