package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.util.Naming

import java.nio.file.Path

object GeneratorSupport {
  val BasePackage = "io.github.stoneream.dachshund"

  def useCasePackage(modulePath: String): String =
    Naming.packageName(s"$BasePackage.usecase", modulePath)

  def handlerPackage(modulePath: String): String =
    Naming.packageName(s"$BasePackage.handler", modulePath)

  def daemonHandlerPackage(modulePath: String): String =
    Naming.packageName(s"$BasePackage.daemon.handler", modulePath)

  def dbAccessorPackage(modulePath: String, kind: String): String =
    Naming.packageName(s"$BasePackage.infra.db", s"$modulePath/$kind")

  def sourcePath(
      repositoryRoot: Path,
      sourceRoot: String,
      basePackagePath: String,
      modulePath: String,
      fileName: String
  ): Path =
    repositoryRoot
      .resolve(sourceRoot)
      .resolve(basePackagePath)
      .resolve(Naming.packagePath(modulePath))
      .resolve(fileName)
      .normalize
}
