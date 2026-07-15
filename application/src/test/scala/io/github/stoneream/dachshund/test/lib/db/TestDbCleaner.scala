package io.github.stoneream.dachshund.test.lib.db

import scalikejdbc.*

private[lib] final class TestDbCleaner(poolName: Symbol) {
  def clean(): Unit =
    NamedDB(poolName).localTx { implicit session =>
      SQL("set foreign_key_checks = 0").execute.apply()
      try {
        tableNames.foreach { tableName =>
          SQL(s"truncate table ${quoteIdentifier(tableName)}").execute.apply()
        }
      } finally {
        SQL("set foreign_key_checks = 1").execute.apply()
      }
    }

  private def tableNames(using DBSession): Seq[String] =
    sql"""
      select table_name
      from information_schema.tables
      where table_schema = database()
        and table_type = 'BASE TABLE'
        and table_name <> 'flyway_schema_history'
      order by table_name
    """
      .map(_.string("table_name"))
      .list
      .apply()

  private def quoteIdentifier(identifier: String): String =
    s"`${identifier.replace("`", "``")}`"
}
