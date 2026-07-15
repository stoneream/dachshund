package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

private[ex] object DbRowValues {
  extension (value: BusinessDateTime) def dbDateTime = value.toLocalDateTime
  extension (value: Option[BusinessDateTime]) def dbDateTime = value.map(_.toLocalDateTime)
  extension (value: AuditUser) def dbAuditUser = value.dbValue
}
