package io.github.stoneream.dachshund.handler.lib

final case class PageMeta(
    title: String,
    withHeader: Boolean = true,
    robots: Option[String] = None
)

object PageMeta {
  val NoIndexNoFollow = "noindex, nofollow"
  val XRobotsTagHeaderName = "X-Robots-Tag"
}
