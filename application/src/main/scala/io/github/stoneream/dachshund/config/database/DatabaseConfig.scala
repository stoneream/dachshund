package io.github.stoneream.dachshund.config.database

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

final case class DatabaseConfig(
    master: DatabasePoolConfig,
    slave: Option[DatabasePoolConfig]
) {
  def allPools: Seq[DatabasePoolConfig] = master +: slave.toSeq
}

object DatabaseConfig {
  private final case class RawDatabaseConfig(
      master: DatabasePoolConfig,
      slave: Option[DatabasePoolConfig]
  ) derives ConfigReader

  given ConfigReader[DatabaseConfig] =
    summon[ConfigReader[RawDatabaseConfig]].emap(validate)

  private def validate(raw: RawDatabaseConfig): Either[CannotConvert, DatabaseConfig] = {
    val databaseConfig = DatabaseConfig(
      master = raw.master,
      slave = raw.slave
    )

    for {
      _ <- validatePool("master", databaseConfig.master)
      _ <- databaseConfig.slave
        .map(validatePool("slave", _))
        .getOrElse(Right(()))
    } yield databaseConfig
  }

  private def validatePool(role: String, pool: DatabasePoolConfig): Either[CannotConvert, Unit] =
    for {
      _ <- requireConfigured(role, "url", pool.url)
      _ <- requireConfigured(role, "user", pool.user)
    } yield ()

  private def requireConfigured(
      role: String,
      fieldName: String,
      value: Option[String]
  ): Either[CannotConvert, Unit] =
    if (value.exists(_.nonEmpty)) {
      Right(())
    } else {
      Left(
        CannotConvert(
          s"db.$role.$fieldName",
          "DatabaseConfig",
          s"db.$role.$fieldName を設定してください"
        )
      )
    }
}
