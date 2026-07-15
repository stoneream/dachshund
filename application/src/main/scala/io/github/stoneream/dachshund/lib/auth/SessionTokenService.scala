package io.github.stoneream.dachshund.lib.auth

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.auth.SessionTokenService.*
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.concurrent.duration.*
import scala.util.Random

@Singleton
class SessionTokenService @Inject() (
    applicationConfig: ApplicationConfig,
    dateTimeService: DateTimeService
) {
  private val secureRandom = Random(new SecureRandom())
  private val sessionSigningConfig = applicationConfig.auth.session.signing
  private val sessionCookieConfig = applicationConfig.cookie.session
  private val base64UrlEncoder = Base64.getUrlEncoder.withoutPadding()

  private val Version = "v1"
  private val MacAlgorithm = "HmacSHA256"
  private val RequiredTokenPartCount = 5
  private val SessionIdLengthBytes = 32
  private val SignatureLengthBytes = 32
  private val ExpectedSessionIdEncodedLength = encodedLength(SessionIdLengthBytes)
  private val ExpectedSignatureEncodedLength = encodedLength(SignatureLengthBytes)
  private val MaxKeyIdLength = 255
  private val MaxIssuedAtLength = 19
  private val DefaultExpiresInSeconds = 2592000L

  def issue(userId: Long): IssuedSessionToken = {
    val keyId = sessionSigningConfig.currentKid
    val now = dateTimeService.now()
    val expiresAt = now.plus(sessionCookieConfig.maxAgeSeconds.getOrElse(DefaultExpiresInSeconds).seconds)
    val issuedAtEpochSecond = now.asOffsetDateTime.toEpochSecond
    val sessionId = randomTokenPart(SessionIdLengthBytes)
    val unsignedToken = buildUnsignedToken(keyId, sessionId, issuedAtEpochSecond)
    val value = s"$unsignedToken.${sign(keyId, unsignedToken)}"

    IssuedSessionToken(
      value = value,
      userId = userId,
      hashedToken = lookupHash(keyId, value),
      sessionId = sessionId,
      issuedAtEpochSecond = issuedAtEpochSecond,
      keyId = keyId,
      issuedAt = now,
      lastAccessedAt = now,
      idleExpiresAt = expiresAt,
      expiresAt = expiresAt
    )
  }

  def verify(rawToken: String): Either[SessionTokenError, ParsedSessionToken] = {
    val parts = rawToken.split("\\.", -1)
    if (parts.length != RequiredTokenPartCount) {
      return Left(SessionTokenError.InvalidFormat)
    }

    val Array(version, keyId, sessionId, issuedAtRaw, signature) = parts
    if (
      version != Version ||
      keyId.isEmpty ||
      keyId.length > MaxKeyIdLength ||
      sessionId.length != ExpectedSessionIdEncodedLength ||
      signature.length != ExpectedSignatureEncodedLength
    ) {
      return Left(SessionTokenError.InvalidFormat)
    }

    val issuedAtEpochSecond =
      if (issuedAtRaw.isEmpty || issuedAtRaw.length > MaxIssuedAtLength || !issuedAtRaw.forall(_.isDigit)) {
        return Left(SessionTokenError.InvalidIssuedAt)
      } else {
        issuedAtRaw.toLongOption match {
          case Some(value) => value
          case None => return Left(SessionTokenError.InvalidIssuedAt)
        }
      }

    if (!sessionSigningConfig.keys.contains(keyId)) {
      return Left(SessionTokenError.UnknownKeyId)
    }

    val unsignedToken = buildUnsignedToken(keyId, sessionId, issuedAtEpochSecond)
    val expectedSignature = sign(keyId, unsignedToken)
    if (
      MessageDigest.isEqual(
        expectedSignature.getBytes(StandardCharsets.US_ASCII),
        signature.getBytes(StandardCharsets.US_ASCII)
      )
    ) {
      Right(
        ParsedSessionToken(
          value = rawToken,
          sessionId = sessionId,
          issuedAtEpochSecond = issuedAtEpochSecond,
          keyId = keyId
        )
      )
    } else {
      Left(SessionTokenError.InvalidSignature)
    }
  }

  def lookupHash(parsedToken: ParsedSessionToken): String =
    lookupHash(parsedToken.keyId, parsedToken.value)

  private def lookupHash(keyId: String, rawToken: String): String =
    base64UrlEncoder.encodeToString(mac(keyId, s"lookup:$rawToken"))

  private def sign(keyId: String, unsignedToken: String): String =
    base64UrlEncoder.encodeToString(mac(keyId, unsignedToken))

  private def mac(keyId: String, value: String): Array[Byte] = {
    val mac = Mac.getInstance(MacAlgorithm)
    mac.init(new SecretKeySpec(sessionSigningConfig.keys(keyId).decodedValue, MacAlgorithm))
    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
  }

  private def buildUnsignedToken(keyId: String, sessionId: String, issuedAtEpochSecond: Long): String =
    s"$Version.$keyId.$sessionId.$issuedAtEpochSecond"

  private def randomTokenPart(lengthBytes: Int): String = {
    val value = new Array[Byte](lengthBytes)
    secureRandom.nextBytes(value)
    base64UrlEncoder.encodeToString(value)
  }

  private def encodedLength(lengthBytes: Int): Int =
    (lengthBytes * 4 + 2) / 3
}

object SessionTokenService {
  final case class IssuedSessionToken(
      value: String,
      userId: Long,
      hashedToken: String,
      sessionId: String,
      issuedAtEpochSecond: Long,
      keyId: String,
      issuedAt: BusinessDateTime,
      lastAccessedAt: BusinessDateTime,
      idleExpiresAt: BusinessDateTime,
      expiresAt: BusinessDateTime
  )

  final case class ParsedSessionToken(
      value: String,
      sessionId: String,
      issuedAtEpochSecond: Long,
      keyId: String
  )

  enum SessionTokenError {
    case InvalidFormat
    case InvalidIssuedAt
    case InvalidSignature
    case UnknownKeyId
  }
}
