package io.github.stoneream.dachshund.test.lib

import io.github.stoneream.dachshund.test.lib.db.DatabaseSupport
import org.scalatest.Suite
import play.api.{Application, Play}

trait PlayApplicationDatabaseSupport extends DatabaseSupport { this: Suite =>
  protected def app: Application

  override protected def startDatabase(): Unit = {
    val _ = app
  }

  override protected def stopDatabase(): Unit =
    Play.stop(app)
}
