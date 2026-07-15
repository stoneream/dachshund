package io.github.stoneream.dachshund.logging

import net.logstash.logback.argument.{StructuredArgument, StructuredArguments}
import org.slf4j
import org.slf4j.LoggerFactory

trait Logger {
  protected final val logger: slf4j.Logger = LoggerFactory.getLogger(getClass)

  protected final val kv: (String, Any) => StructuredArgument = StructuredArguments.kv(_: String, _: Any)

  def mask(value: String, unmaskedLength: Int = 4, maskStr: String = "*"): String = {
    if (value.length <= unmaskedLength) {
      value
    } else {
      value.take(unmaskedLength) + maskStr * (value.length - unmaskedLength)
    }
  }

  protected final def optionalValue(value: Option[String]): String =
    value.map(_.trim).filter(_.nonEmpty).getOrElse("(unset)")

  protected final def maskedValue(value: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).map("*" * _.length).getOrElse("(unset)")

  protected final def maskedValue(value: Option[String]): String =
    value.map(_.trim).filter(_.nonEmpty).map(maskedValue).getOrElse("(unset)")
}
