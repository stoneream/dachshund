package io.github.stoneream.dachshund.lib.auth

import io.github.stoneream.dachshund.lib.auth.SessionTokenService.SessionTokenError
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec

import scala.concurrent.duration.*

class SessionTokenServiceSpec extends AnyFeatureSpec with IdiomaticMockito {
  Feature("Session token service") {
    Scenario("発行した token を検証し DB lookup 用 hash を再計算できる") {
      val service = sessionTokenService()

      val issued = service.issue(userId = 123L)
      val parsed = service.verify(issued.value).toOption.get

      assert(parsed.value == issued.value)
      assert(parsed.sessionId == issued.sessionId)
      assert(parsed.keyId == "v1")
      assert(issued.hashedToken == service.lookupHash(parsed))
      assert(issued.hashedToken != issued.value)
      assert(issued.expiresAt.toLocalDateTime == fixedNow.plus(30.days).toLocalDateTime)
    }

    Scenario("署名が改ざんされた token は検証に失敗する") {
      val service = sessionTokenService()
      val issued = service.issue(userId = 123L)

      val result = service.verify(replaceLastCharacter(issued.value))

      assert(result == Left(SessionTokenError.InvalidSignature))
    }

    Scenario("未設定の key id を持つ token は検証に失敗する") {
      val service = sessionTokenService()
      val issued = service.issue(userId = 123L)

      val result = service.verify(issued.value.replaceFirst("\\.v1\\.", ".v2."))

      assert(result == Left(SessionTokenError.UnknownKeyId))
    }
  }

  private def sessionTokenService(): SessionTokenService = {
    val dateTimeService = mock[DateTimeService]
    dateTimeService.now() returns fixedNow

    new SessionTokenService(
      applicationConfig = TestApplicationConfig(),
      dateTimeService = dateTimeService
    )
  }

  private def replaceLastCharacter(value: String): String = {
    val replacement = if (value.last == 'A') 'B' else 'A'
    value.dropRight(1) + replacement
  }

  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")
}
