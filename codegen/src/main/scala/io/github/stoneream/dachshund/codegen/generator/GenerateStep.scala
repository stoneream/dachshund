package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.NamedPathRequest
import io.github.stoneream.dachshund.codegen.model.{GeneratedArtifacts, GeneratedFile}
import io.github.stoneream.dachshund.codegen.util.Naming

import java.nio.file.Path

object GenerateStep {
  def render(
      request: NamedPathRequest,
      repositoryRoot: Path
  ): GeneratedArtifacts = {
    val packageName = s"${GeneratorSupport.useCasePackage(request.modulePath)}.step"
    val className = Naming.typeNameWithSuffix(request.name, "Step")
    val packageScope = Naming.lastPackageSegment(request.modulePath)
    val path = GeneratorSupport
      .sourcePath(
        repositoryRoot = repositoryRoot,
        sourceRoot = "application/src/main/scala",
        basePackagePath = "io/github/stoneream/dachshund/usecase",
        modulePath = s"${request.modulePath}/step",
        fileName = s"$className.scala"
      )

    GeneratedArtifacts(
      files = Seq(
        GeneratedFile(path, step(packageName, packageScope, className))
      )
    )
  }

  private def step(
      packageName: String,
      packageScope: String,
      className: String
  ): String =
    // language=scala 3
    s"""package $packageName
       |
       |import io.github.stoneream.dachshund.logging.TraceLogger
       |import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
       |
       |import com.google.inject.{Inject, Singleton}
       |import scala.concurrent.Future
       |
       |@Singleton
       |private[$packageScope] class $className @Inject() () extends TraceLogger {
       |  def run()(using LoggingContext): Future[Unit] =
       |    Future.successful(())
       |}
       |""".stripMargin
}
