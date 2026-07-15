package io.github.stoneream.dachshund.controller.lib

sealed abstract class ControllerError(
    e: Throwable = null
) extends Throwable(e) {
  val statusCode: Int
  val title: String
  val detail: Option[String]
  val cause: Option[String]
  def isServerError: Boolean = statusCode / 100 == 5
}

object ControllerError {
  final case class InvalidParameter(
      param: String,
      e: Throwable
  ) extends ControllerError(e) {
    override val statusCode: Int = 400
    override val title: String = "パラメーター不正"
    override val detail: Option[String] = Some(s"パラメーター $param が不正です")
    override val cause: Option[String] = Option(e).map(_.getMessage)
  }
}
