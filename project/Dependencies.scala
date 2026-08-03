import sbt._

object Dependencies {

  private object versions {
    val jackson2 = "2.17.0"
    val scalafixRules = "0.6.27"
    val mockitoScala = "2.2.3"
    val caffeine = "3.2.4"
  }

  lazy val scalafixRules: ModuleID =
    "com.github.xuwei-k" %% "scalafix-rules" % versions.scalafixRules

  // 依存がぶつかるためバージョンを固定している
  lazy val jackson2Overrides: Seq[ModuleID] = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % versions.jackson2,
    "com.fasterxml.jackson.core" % "jackson-databind" % versions.jackson2,
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % versions.jackson2,
    "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % versions.jackson2,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % versions.jackson2,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % versions.jackson2,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % versions.jackson2
  )

  lazy val application: Seq[ModuleID] = Seq(
    circe,
    caffeine,
    sttp,
    spotifyWebApiJava,
    pureconfig,
    scalikejdbc,
    mysqlConnector,
    scalaTest,
    logCaptor
  ).flatten

  lazy val daemon: Seq[ModuleID] = Seq(
    zio,
    pureconfig,
    hikariCp,
    mysqlConnector,
    scalaTest,
    logCaptor
  ).flatten

  lazy val server: Seq[ModuleID] = Seq(
    circe,
    scalikejdbc,
    hikariCp,
    mysqlConnector,
    scalaTest,
    logCaptor,
    playScalaTest
  ).flatten

  lazy val logging: Seq[ModuleID] = Seq(
    loggingCore,
    scalaTest,
    logCaptor
  ).flatten

  lazy val codegen: Seq[ModuleID] = Seq(
    circe,
    scopt,
    scalaTest
  ).flatten

  lazy val loggingCore: Seq[ModuleID] = Seq(
    "ch.qos.logback" % "logback-classic" % "1.6.1",
    "net.logstash.logback" % "logstash-logback-encoder" % "9.0"
  )

  lazy val circeVersion = "0.14.15"
  lazy val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )

  lazy val sttpVersion = "3.10.3"
  lazy val sttp: Seq[ModuleID] = Seq(
    "com.softwaremill.sttp.client3" %% "core" % sttpVersion,
    "com.softwaremill.sttp.client3" %% "circe" % sttpVersion
  )

  lazy val scopt: Seq[ModuleID] = Seq(
    "com.github.scopt" %% "scopt" % "4.1.0"
  )

  lazy val caffeine: Seq[ModuleID] = Seq(
    "com.github.ben-manes.caffeine" % "caffeine" % versions.caffeine
  )

  lazy val spotifyWebApiJava: Seq[ModuleID] = Seq(
    "se.michaelthelin.spotify" % "spotify-web-api-java" % "9.4.0"
  )

  lazy val scalaTest: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    mockitoScalaTest
  )

  lazy val mockitoScalaTest: ModuleID =
    "org.mockito" %% "mockito-scala-scalatest" % versions.mockitoScala % Test

  lazy val logCaptor: Seq[ModuleID] = Seq(
    "io.github.hakky54" % "logcaptor" % "2.12.6" % Test
  )

  lazy val playScalaTest: Seq[ModuleID] = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
  )

  lazy val scalikejdbcVersion = "4.3.5"
  lazy val scalikejdbc: Seq[ModuleID] = Seq(
    "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-syntax-support-macro" % scalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-config" % scalikejdbcVersion,
    "org.scalikejdbc" %% "scalikejdbc-test" % scalikejdbcVersion
  )

  lazy val hikariCp: Seq[ModuleID] = Seq(
    "com.zaxxer" % "HikariCP" % "7.1.0"
  )

  lazy val mysqlConnector: Seq[ModuleID] = Seq(
    "com.mysql" % "mysql-connector-j" % "26.7.0"
  )

  lazy val pureconfig: Seq[ModuleID] = Seq(
    "com.github.pureconfig" %% "pureconfig-core" % "0.17.10"
  )

  lazy val zioVersion = "2.1.26"
  lazy val zio: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio" % zioVersion
  )
}
