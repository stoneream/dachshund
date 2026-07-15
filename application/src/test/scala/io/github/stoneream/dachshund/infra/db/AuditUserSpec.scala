package io.github.stoneream.dachshund.infra.db

import org.scalatest.featurespec.AnyFeatureSpec

class AuditUserSpec extends AnyFeatureSpec {
  Feature("AuditUser") {
    Scenario("empty actor の DB 保存値を返す") {
      assert(AuditUser.Empty.dbValue == "")
      assert(AuditUser.EmptyDeletedUser == "")
    }

    Scenario("system actor の DB 保存値を返す") {
      assert(AuditUser.System.dbValue == "system")
    }

    Scenario("user actor の DB 保存値を返す") {
      assert(AuditUser.User(123L).dbValue == "user:123")
    }
  }
}
