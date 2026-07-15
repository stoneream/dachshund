package io.github.stoneream.dachshund.daemon.config

import pureconfig.error.CannotConvert

final case class JobName(value: String) {
  override def toString: String = value
}

object JobName {
  private[config] val SafePattern = "^[a-z0-9][a-z0-9_.-]{0,63}$"

  def validate(value: String, path: String): Either[CannotConvert, JobName] =
    DaemonConfigValidation.safeJobName(path, value)
}
