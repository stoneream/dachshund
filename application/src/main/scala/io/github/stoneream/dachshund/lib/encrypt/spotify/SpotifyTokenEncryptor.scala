package io.github.stoneream.dachshund.lib.encrypt.spotify

import io.github.stoneream.dachshund.config.ApplicationConfig

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import com.google.inject.{Inject, Singleton}

@Singleton
class SpotifyTokenEncryptor @Inject() (
    applicationConfig: ApplicationConfig
) {
  private val algorithm = "AES-256-GCM"
  private val transformation = "AES/GCM/NoPadding"
  private val keyAlgorithm = "AES"
  private val keyBytes = 32
  private val nonceBytes = 12
  private val tagBytes = 16
  private val tagBits = tagBytes * 8
  private val secureRandom = new SecureRandom()

  def encrypt(token: String, aad: Option[String] = None): EncryptedSpotifyToken = {
    val nonce = new Array[Byte](nonceBytes)
    secureRandom.nextBytes(nonce)

    val cipher = Cipher.getInstance(transformation)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(tagBits, nonce))
    aad.foreach(value => cipher.updateAAD(value.getBytes(StandardCharsets.UTF_8)))

    val encryptedWithTag = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8))
    val cipherText = encryptedWithTag.take(encryptedWithTag.length - tagBytes)
    val tag = encryptedWithTag.takeRight(tagBytes)

    EncryptedSpotifyToken(
      cipherText = cipherText,
      nonce = nonce,
      tag = tag,
      algorithm = algorithm,
      keyVersion = applicationConfig.spotify.token.encryptionKeyVersion
    )
  }

  def decrypt(encrypted: EncryptedSpotifyToken, aad: Option[String] = None): String = {
    val cipher = Cipher.getInstance(transformation)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(tagBits, encrypted.nonce))
    aad.foreach(value => cipher.updateAAD(value.getBytes(StandardCharsets.UTF_8)))

    val plaintext = cipher.doFinal(encrypted.cipherText ++ encrypted.tag)
    String(plaintext, StandardCharsets.UTF_8)
  }

  private val keySpec: SecretKeySpec = buildKeySpec()

  private def buildKeySpec(): SecretKeySpec = {
    val encodedKey = applicationConfig.spotify.token.encryptionKey.trim

    if (encodedKey.isEmpty) {
      throw new SpotifyTokenEncryptionException("Spotify トークン暗号化キーが設定されていません")
    }

    val decodedKey =
      try {
        Base64.getDecoder.decode(encodedKey)
      } catch {
        case e: IllegalArgumentException =>
          throw new SpotifyTokenEncryptionException("Spotify トークン暗号化キーは Base64 形式である必要があります", e)
      }

    if (decodedKey.length != keyBytes) {
      throw new SpotifyTokenEncryptionException("Spotify トークン暗号化キーは 32 バイトにデコードできる必要があります")
    }

    SecretKeySpec(decodedKey, keyAlgorithm)
  }
}
