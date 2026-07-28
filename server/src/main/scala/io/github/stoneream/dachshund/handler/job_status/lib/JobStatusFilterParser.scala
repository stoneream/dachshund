package io.github.stoneream.dachshund.handler.job_status.lib

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
      rawValues.iterator
        .map(_.trim)
        .flatMap(QueueJobStatus.fromString)
        .toSet
    }
  }

  def rejectStatuses(request: RequestHeader): Unit = {
    val rawValues = request.queryString.get(StatusParamName).toSeq.flatten

    if (rawValues.nonEmpty) {
      throw new IllegalArgumentException("status は指定できません")
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
