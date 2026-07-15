package io.github.stoneream.dachshund.infra.db.transaction

import io.github.stoneream.dachshund.config.ApplicationConfig
import scalikejdbc.{DBSession, NamedDB}

import com.google.inject.{Inject, Singleton}

@Singleton
class DatabaseTransaction @Inject() (
    applicationConfig: ApplicationConfig
) {
  def localTx[A](role: DatabaseRole)(body: DBSession => A): A =
    NamedDB(connectionPoolName(role)).localTx(body)

  def readOnly[A](role: DatabaseRole)(body: DBSession => A): A =
    NamedDB(connectionPoolName(role)).readOnly(body)

  private def connectionPoolName(role: DatabaseRole): Symbol =
    role match {
      case DatabaseRole.Master => applicationConfig.db.master.connectionPoolName
      case DatabaseRole.Slave =>
        applicationConfig.db.slave
          .getOrElse(applicationConfig.db.master)
          .connectionPoolName
    }
}
