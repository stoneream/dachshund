package io.github.stoneream.dachshund.config.database

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

final case class DatabasePoolConfig(
    name: String,
    driver: Option[String],
    url: Option[String],
    user: Option[String],
    password: Option[String],
    hikari: HikariConfig
) {
  def connectionPoolName: Symbol = Symbol(name)

  def configuredDriver: Option[String] = driver

  def requireUrl: String = DatabasePoolConfig.requireValidatedField("url", url)

  def requireUser: String = DatabasePoolConfig.requireValidatedField("user", user)
}

object DatabasePoolConfig {
  private val BlankErrorSuffix = "は空にできません"
  private val LeadingOrTrailingWhitespaceErrorSuffix =
    "の先頭または末尾に空白を含めることはできません"

  private final case class RawDatabasePoolConfig(
      name: String,
      driver: Option[String],
      url: Option[String],
      user: Option[String],
      password: Option[String],
      hikari: HikariConfig
  ) derives ConfigReader

  given ConfigReader[DatabasePoolConfig] =
    summon[ConfigReader[RawDatabasePoolConfig]].emap(validate)

  private def validate(raw: RawDatabasePoolConfig): Either[CannotConvert, DatabasePoolConfig] = {
    for {
      validatedName <- validateRequiredTrimmedText("db.<pool>.name", raw.name)
      validatedDriver <- validateOptionalTrimmedText("db.<pool>.driver", raw.driver)
      validatedUrl <- validateOptionalTrimmedText("db.<pool>.url", raw.url)
      validatedUser <- validateOptionalTrimmedText("db.<pool>.user", raw.user)
      validatedPassword <- validateOptionalNonBlankText("db.<pool>.password", raw.password)
    } yield {
      DatabasePoolConfig(
        name = validatedName,
        driver = validatedDriver,
        url = validatedUrl,
        user = validatedUser,
        password = validatedPassword,
        hikari = raw.hikari
      )
    }
  }

  private def validateOptionalTrimmedText(
      fieldName: String,
      value: Option[String]
  ): Either[CannotConvert, Option[String]] =
    value match {
      case None => Right(None)
      case Some(text) => validateRequiredTrimmedText(fieldName, text).map(Some(_))
    }

  private def validateOptionalNonBlankText(
      fieldName: String,
      value: Option[String]
  ): Either[CannotConvert, Option[String]] =
    value match {
      case None => Right(None)
      case Some(text) => validateRequiredNonBlankText(fieldName, text).map(Some(_))
    }

  private def validateRequiredTrimmedText(
      fieldName: String,
      value: String
  ): Either[CannotConvert, String] =
    if (value.trim.isEmpty) {
      Left(validationError(fieldName, BlankErrorSuffix))
    } else if (value != value.trim) {
      Left(validationError(fieldName, LeadingOrTrailingWhitespaceErrorSuffix))
    } else {
      Right(value)
    }

  private def validateRequiredNonBlankText(
      fieldName: String,
      value: String
  ): Either[CannotConvert, String] =
    if (value.isEmpty) {
      Left(validationError(fieldName, "は空にできません"))
    } else {
      Right(value)
    }

  private def validationError(fieldName: String, message: String): CannotConvert =
    CannotConvert("", "DatabasePoolConfig", s"$fieldName $message")

  private def requireValidatedField(fieldName: String, value: Option[String]): String =
    value.getOrElse {
      throw new IllegalStateException(
        s"起動時に検証済みのデータベース設定 '$fieldName' が見つかりません"
      )
    }
}
