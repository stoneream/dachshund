package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.model.{GeneratedArtifacts, GeneratedFile}

import java.nio.file.Path

object GenerateDbAccessor {
  def render(
      modulePath: String,
      readerName: String,
      writerName: String,
      repositoryRoot: Path
  ): GeneratedArtifacts =
    GeneratedArtifacts(
      files = Seq(
        GeneratedFile(
          GeneratorSupport.sourcePath(
            repositoryRoot = repositoryRoot,
            sourceRoot = "application/src/main/scala",
            basePackagePath = "io/github/stoneream/dachshund/infra/db",
            modulePath = s"$modulePath/reader",
            fileName = s"$readerName.scala"
          ),
          reader(GeneratorSupport.dbAccessorPackage(modulePath, "reader"), readerName)
        ),
        GeneratedFile(
          GeneratorSupport.sourcePath(
            repositoryRoot = repositoryRoot,
            sourceRoot = "application/src/main/scala",
            basePackagePath = "io/github/stoneream/dachshund/infra/db",
            modulePath = s"$modulePath/writer",
            fileName = s"$writerName.scala"
          ),
          writer(GeneratorSupport.dbAccessorPackage(modulePath, "writer"), writerName)
        )
      )
    )

  // language=scala 3
  private def reader(packageName: String, readerName: String): String =
    s"""package $packageName
       |
       |import com.google.inject.{Inject, Singleton}
       |
       |@Singleton
       |class $readerName @Inject() () {
       |  // Add usecase-specific read methods here. Public names should describe query intent.
       |}
       |""".stripMargin

  // language=scala 3
  private def writer(packageName: String, writerName: String): String =
    s"""package $packageName
       |
       |import com.google.inject.{Inject, Singleton}
       |
       |@Singleton
       |class $writerName @Inject() () {
       |  // Add usecase-specific write methods here. Public names should describe state transitions.
       |}
       |""".stripMargin
}
