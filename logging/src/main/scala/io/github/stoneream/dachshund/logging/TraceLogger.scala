package io.github.stoneream.dachshund.logging

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import net.logstash.logback.argument.{StructuredArgument, StructuredArguments}
import org.slf4j
import org.slf4j.LoggerFactory

trait TraceLogger {
  private final val logger: slf4j.Logger = LoggerFactory.getLogger(getClass)

  def debug(message: String, arguments: Any*)(using context: LoggingContext): Unit = {
    logger.debug(message, withTraceId(arguments)*)
  }

  def info(message: String, arguments: Any*)(using context: LoggingContext): Unit = {
    logger.info(message, withTraceId(arguments)*)
  }

  def warn(message: String, arguments: Any*)(using context: LoggingContext): Unit = {
    logger.warn(message, withTraceId(arguments)*)
  }

  def error(message: String, arguments: Any*)(using context: LoggingContext): Unit = {
    logger.error(message, withTraceId(arguments)*)
  }

  protected final val kv: (String, Any) => StructuredArgument = StructuredArguments.kv(_: String, _: Any)

  private def withTraceId(arguments: Seq[Any])(using context: LoggingContext): Seq[Any] =
    arguments :+ kv("traceId", context.traceId)

  def mask(value: String, unmaskedLength: Int = 4, maskStr: String = "*"): String = {
    if (value.length <= unmaskedLength) {
      value
    } else {
      value.take(unmaskedLength) + maskStr * (value.length - unmaskedLength)
    }
  }
}

object TraceLogger {
  final case class LoggingContext(
      traceId: String
  )
}
