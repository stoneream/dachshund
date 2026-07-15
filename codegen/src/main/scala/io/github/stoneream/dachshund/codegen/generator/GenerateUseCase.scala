package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.NamedPathRequest
import io.github.stoneream.dachshund.codegen.model.{GeneratedArtifacts, GeneratedFile}
import io.github.stoneream.dachshund.codegen.util.Naming

import java.nio.file.Path

object GenerateUseCase {
  def render(
      request: NamedPathRequest,
      repositoryRoot: Path
  ): GeneratedArtifacts = {
    val packageName = GeneratorSupport.useCasePackage(request.modulePath)
    val useCaseName = Naming.typeNameWithSuffix(request.name, "UseCase")
    val inputName = s"${useCaseName}Input"
    val outputName = s"${useCaseName}Output"
    val exceptionName = s"${useCaseName}Exception"
    val basePath = repositoryRoot
      .resolve("application/src/main/scala/io/github/stoneream/dachshund/usecase")
      .resolve(Naming.packagePath(request.modulePath))
      .normalize

    GeneratedArtifacts(
      files = Seq(
        GeneratedFile(basePath.resolve(s"$inputName.scala"), useCaseInput(packageName, inputName)),
        GeneratedFile(basePath.resolve(s"$outputName.scala"), useCaseOutput(packageName, outputName)),
        GeneratedFile(basePath.resolve(s"$exceptionName.scala"), useCaseException(packageName, exceptionName)),
        GeneratedFile(basePath.resolve(s"$useCaseName.scala"), useCase(packageName, useCaseName, inputName, outputName, exceptionName))
      )
    )
  }

  private def useCaseInput(packageName: String, inputName: String): String =
    // language=scala 3
    s"""package $packageName
       |
       |final case class $inputName()
       |""".stripMargin

  private def useCaseOutput(packageName: String, outputName: String): String =
    // language=scala 3
    s"""package $packageName
       |
       |final case class $outputName()
       |""".stripMargin

  private def useCaseException(packageName: String, exceptionName: String): String =
    // language=scala 3
    s"""package $packageName
       |
       |sealed abstract class $exceptionName(
       |    message: String,
       |    cause: Throwable = null
       |) extends Exception(message, cause)
       |
       |object $exceptionName {
       |  final case class Unexpected(cause: Throwable) extends $exceptionName("Unexpected use case error", cause)
       |}
       |""".stripMargin

  private def useCase(
      packageName: String,
      useCaseName: String,
      inputName: String,
      outputName: String,
      exceptionName: String
  ): String =
    // language=scala 3
    s"""package $packageName
       |
       |import io.github.stoneream.dachshund.logging.TraceLogger
       |import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
       |import io.github.stoneream.dachshund.usecase.UseCase
       |import $packageName.{$exceptionName as UseCaseException, $inputName as UseCaseInput, $outputName as UseCaseOutput}
       |
       |import com.google.inject.{Inject, Singleton}
       |import scala.concurrent.Future
       |
       |@Singleton
       |class $useCaseName @Inject() ()
       |    extends UseCase[
       |      UseCaseInput,
       |      UseCaseOutput,
       |      UseCaseException
       |    ]
       |    with TraceLogger {
       |
       |  override def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput] = {
       |    info("Run ${Naming.toKebabCase(useCaseName)}")
       |    Future.successful(UseCaseOutput())
       |  }
       |}
       |""".stripMargin
}
