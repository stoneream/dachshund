package io.github.stoneream.dachshund.daemon.config

import pureconfig.error.CannotConvert

import scala.concurrent.duration.{Duration, FiniteDuration}

private[config] object DaemonConfigValidation {
  def positiveInt(path: String, value: Int): Either[CannotConvert, Int] =
    Either.cond(value > 0, value, validationError(path, "は正の値である必要があります"))

  def positiveDuration(path: String, value: FiniteDuration): Either[CannotConvert, FiniteDuration] =
    Either.cond(value > Duration.Zero, value, validationError(path, "は正の値である必要があります"))

  def nonNegativeDuration(path: String, value: FiniteDuration): Either[CannotConvert, FiniteDuration] =
    Either.cond(value >= Duration.Zero, value, validationError(path, "は負数にできません"))

  def nonNegativeDoubleOption(path: String, value: Option[Double]): Either[CannotConvert, Option[Double]] =
    value match {
      case Some(number) => Either.cond(number >= 0.0, value, validationError(path, "は負数にできません"))
      case None => Right(None)
    }

  def safeJobName(path: String, value: String): Either[CannotConvert, JobName] =
    Either.cond(
      value.matches(JobName.SafePattern),
      JobName(value),
      validationError(path, "は小文字英数字、ハイフン、アンダースコア、ドットのみ利用できます")
    )

  private def validationError(path: String, message: String): CannotConvert =
    CannotConvert("", "DaemonConfig", s"$path $message")
}
