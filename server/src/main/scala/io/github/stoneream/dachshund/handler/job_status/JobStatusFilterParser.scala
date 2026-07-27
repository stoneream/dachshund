package io.github.stoneream.dachshund.handler.job_status

import io.github.stoneream.dachshund.model.QueueJobStatus
import play.api.mvc.RequestHeader

object JobStatusFilterParser {
  val DetailLimit: Int = 100
  private val StatusParamName = "status"
  private val PageParamName = "page"

  def selectedStatuses(request: RequestHeader): Set[QueueJobStatus] = {
    val rawValues = request.queryString.get(StatusParamName).toSeq.flatten

    if (rawValues.isEmpty) {
      QueueJobStatus.values.toSet
    } else {
      rawValues.map(parseStatus).toSet
    }
  }

  def selectedPage(request: RequestHeader): Int = {
    val rawValues = request.queryString.get(PageParamName).toSeq.flatten

    rawValues match {
      case Seq() => 1
      case Seq(value) => parsePage(value)
      case _ => throw new IllegalArgumentException("page が複数指定されています")
    }
  }

  private def parseStatus(value: String): QueueJobStatus = {
    val normalized = value.trim
    if (normalized.isEmpty) {
      throw new IllegalArgumentException("status が空です")
    }

    QueueJobStatus.values
      .find(_.dbValue == normalized)
      .getOrElse(throw new IllegalArgumentException(s"status が想定外です: $normalized"))
  }

  private def parsePage(value: String): Int = {
    val normalized = value.trim
    if (normalized.isEmpty) {
      throw new IllegalArgumentException("page が空です")
    }

    normalized.toIntOption.filter(_ > 0).getOrElse {
      throw new IllegalArgumentException(s"page が想定外です: $normalized")
    }
  }
}
