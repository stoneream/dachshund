package io.github.stoneream.dachshund.test.lib.db

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseTransaction
import io.github.stoneream.dachshund.lib.executor.Executors.DatabaseExecutor
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import io.github.stoneream.dachshund.test.lib.db.{DirectDatabaseExecutor, TestDatabaseConnection, TestDbCleaner}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait DatabaseSupport extends BeforeAndAfterAll with BeforeAndAfterEach { this: Suite =>
  protected lazy val testApplicationConfig: ApplicationConfig = TestApplicationConfig()
  protected lazy val databaseTransaction: DatabaseTransaction = new DatabaseTransaction(testApplicationConfig)
  protected val databaseExecutor: DatabaseExecutor = DirectDatabaseExecutor

  private lazy val cleaner = new TestDbCleaner(testApplicationConfig.db.master.connectionPoolName)

  protected def startDatabase(): Unit =
    TestDatabaseConnection.initialize(testApplicationConfig)

  protected def stopDatabase(): Unit =
    TestDatabaseConnection.close(testApplicationConfig)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startDatabase()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    cleaner.clean()
  }

  override protected def afterEach(): Unit =
    try {
      cleaner.clean()
    } finally {
      super.afterEach()
    }

  override protected def afterAll(): Unit =
    try {
      stopDatabase()
    } finally {
      super.afterAll()
    }
}
