// === project info ===

inThisBuild(
  Seq(
    organization := "io.github.stoneream",
    developers := List(
      Developer(
        "stoneream",
        "Ishikawa Ryuto",
        "ishikawa-r@protonmail.com",
        url("https://github.com/stoneream")
      )
    )
  )
)

// === scala settings ===

inThisBuild(
  Seq(
    scalaVersion := "3.3.7",
    scalafmtOnCompile := true,
    scalacOptions ++= Seq(
      "-no-indent",
      "-Yretain-trees",
      "-Wunused:all"
    ),
    scalafixDependencies += Dependencies.scalafixRules,
    semanticdbEnabled := true,
    dependencyOverrides ++= Dependencies.jackson2Overrides,
    Test / fork := false,
    Test / parallelExecution := false
  )
)

Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

// === project setting ===

lazy val ensureTaggedReleaseForDocker = taskKey[Unit](
  "Fail docker publish tasks unless HEAD is on an exact Git tag."
)

def dockerCompatibleTag(value: String): String =
  value.replaceAll("[^A-Za-z0-9_.-]", "-")

def releaseIdentifier(fallback: String): String =
  sys.env.getOrElse("DOCKER_IMAGE_TAG", fallback)

lazy val buildMetadataResourceName = "dachshund-build.properties"
lazy val assetVersionPropertyName = "asset.version"

lazy val serverBuildMetadataSettings = Seq(
  Compile / resourceGenerators += Def.task {
    val file = (Compile / resourceManaged).value / buildMetadataResourceName
    val assetVersion = releaseIdentifier(version.value)
    IO.write(file, s"$assetVersionPropertyName=$assetVersion\n")
    Seq(file)
  }.taskValue
)

lazy val dockerImageSettings = Seq(
  dockerBaseImage := "azul/zulu-openjdk:21-latest",
  dockerRepository := Some("ghcr.io"),
  dockerUsername := Some("stoneream"),
  dockerUpdateLatest := false,
  Docker / version := dockerCompatibleTag(
    sys.env.getOrElse("DOCKER_IMAGE_TAG", version.value)
  ),
  ensureTaggedReleaseForDocker := {
    val v = version.value
    if (v.contains("+")) {
      sys.error(
        s"HEAD は正確な Git タグを指していません (現在の派生バージョン: $v) " +
          "タグを作成して checkout してから docker publish を実行してください"
      )
    }
  },
  Docker / publish := (Docker / publish).dependsOn(ensureTaggedReleaseForDocker).value
)

lazy val root = (project in file("."))
  .settings(
    name := "dachshund",
    publish / skip := true
  )
  .aggregate(application, daemon, server, logging, codegen)

lazy val application = (project in file("application"))
  .settings(
    name := "dachshund-application",
    libraryDependencies ++= Dependencies.application,
    libraryDependencies += guice
  )
  .dependsOn(logging % "compile->compile; test->test")

lazy val daemon = (project in file("daemon"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    name := "dachshund-daemon",
    libraryDependencies ++= Dependencies.daemon,
    libraryDependencies += guice,
    Compile / run / fork := true,
    Compile / run / outputStrategy := Some(StdoutOutput),
    Compile / mainClass := Some("io.github.stoneream.dachshund.daemon.DaemonMain"),
    Docker / packageName := s"${name.value}"
  )
  .settings(dockerImageSettings)
  .dependsOn(application % "compile->compile; test->test")
  .dependsOn(logging % "compile->compile; test->test")

lazy val server = (project in file("server"))
  .enablePlugins(PlayScala)
  .enablePlugins(DockerPlugin)
  .disablePlugins(PlayLayoutPlugin)
  .settings(
    name := "dachshund-server",
    libraryDependencies ++= Dependencies.server,
    libraryDependencies += guice,
    assembly / assemblyJarName := s"${name.value}.jar",
    dockerExposedPorts := Seq(9000),
    Docker / packageName := s"${name.value}"
  )
  .settings(dockerImageSettings)
  .settings(serverBuildMetadataSettings)
  .dependsOn(application % "compile->compile; test->test")
  .dependsOn(logging % "compile->compile; test->test")

lazy val logging = (project in file("logging"))
  .settings(
    name := "dachshund-logging",
    libraryDependencies ++= Dependencies.logging
  )

lazy val codegen = (project in file("codegen"))
  .settings(
    name := "dachshund-codegen",
    libraryDependencies ++= Dependencies.codegen,
    publish / skip := true,
    assembly / mainClass := Some("io.github.stoneream.dachshund.codegen.DachshundCodeGenMain"),
    assembly / assemblyJarName := s"${name.value}.jar"
  )
