package io.github.stoneream.dachshund.spotify.auth

import io.github.stoneream.dachshund.lib.encrypt.spotify.SpotifyTokenEncryptor
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import pureconfig.error.ConfigReaderException

import java.util.Base64
import javax.crypto.AEADBadTagException

class SpotifyTokenEncryptorSpec extends AnyFeatureSpec with Matchers {
  Feature("Spotify token の暗号化") {
    Scenario("additional authenticated data 付きで token を暗号化・復号できる") {
      val encryptor = new SpotifyTokenEncryptor(TestApplicationConfig(encryptionKey = base64Key))

      val encrypted = encryptor.encrypt("access-token-value", Some("user:1"))
      val decrypted = encryptor.decrypt(encrypted, Some("user:1"))

      decrypted shouldBe "access-token-value"
      encrypted.algorithm shouldBe "AES-256-GCM"
      encrypted.keyVersion shouldBe "v1"
      encrypted.nonce.length shouldBe 12
      encrypted.tag.length shouldBe 16
      encrypted.cipherText should not be empty
    }

    Scenario("additional authenticated data が異なる場合は復号を拒否する") {
      val encryptor = new SpotifyTokenEncryptor(TestApplicationConfig(encryptionKey = base64Key))
      val encrypted = encryptor.encrypt("access-token-value", Some("user:1"))

      assertThrows[AEADBadTagException] {
        encryptor.decrypt(encrypted, Some("user:2"))
      }
    }

    Scenario("暗号化設定が有効な場合は初期化できる") {
      noException should be thrownBy new SpotifyTokenEncryptor(TestApplicationConfig(encryptionKey = base64Key))
    }

    Scenario("暗号化鍵が未設定の場合は初期化を拒否する") {
      assertThrows[ConfigReaderException[?]] {
        new SpotifyTokenEncryptor(TestApplicationConfig(encryptionKey = ""))
      }
    }
  }

  private def base64Key: String =
    Base64.getEncoder.encodeToString(Array.tabulate[Byte](32)(_.toByte))
}
