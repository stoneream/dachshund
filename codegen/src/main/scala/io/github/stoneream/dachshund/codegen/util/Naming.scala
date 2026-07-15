package io.github.stoneream.dachshund.codegen.util

object Naming {
  private val UpperCamelPattern = "^[A-Z][A-Za-z0-9]*$".r
  private val ModulePathPattern = "^[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)*$".r
  private val DbNamePattern = "^[a-z][a-z0-9_]*$".r

  def normalizeModulePath(value: String): String =
    value.trim.stripPrefix("/").stripSuffix("/")

  def validateUpperCamel(value: String, optionName: String): Either[String, Unit] =
    Either.cond(
      UpperCamelPattern.matches(value),
      (),
      s"$optionName は UpperCamelCase で指定してください"
    )

  def validateModulePath(value: String, optionName: String): Either[String, Unit] = {
    val normalized = normalizeModulePath(value)
    Either.cond(
      ModulePathPattern.matches(normalized),
      (),
      s"$optionName は slash 区切りの小文字 path で指定してください"
    )
  }

  def validateDbName(value: String, optionName: String): Either[String, Unit] =
    Either.cond(
      DbNamePattern.matches(value),
      (),
      s"$optionName は snake_case で指定してください"
    )

  def packageName(basePackage: String, modulePath: String): String =
    (Seq(basePackage) ++ packageSegments(modulePath)).mkString(".")

  def packagePath(modulePath: String): String =
    packageSegments(modulePath).mkString("/")

  def lastPackageSegment(modulePath: String): String =
    packageSegments(modulePath).last

  def typeNameWithSuffix(name: String, suffix: String): String =
    if (name.endsWith(suffix)) name else s"$name$suffix"

  def toLowerCamel(name: String): String =
    name.headOption.fold(name)(first => s"${first.toLower}${name.drop(1)}")

  def toUpperCamelFromSnake(name: String): String =
    name.split("_").filter(_.nonEmpty).map(segment => s"${segment.head.toUpper}${segment.drop(1)}").mkString

  def toSnakeCase(name: String): String = {
    val normalized = name.replace('-', '_')
    normalized.zipWithIndex.foldLeft("") { case (result, (char, index)) =>
      if (char.isUpper && index > 0 && result.lastOption.exists(_ != '_')) {
        result + "_" + char.toLower
      } else {
        result + char.toLower
      }
    }
  }

  def toKebabCase(name: String): String =
    toSnakeCase(name).replace('_', '-')

  private def packageSegments(modulePath: String): Seq[String] =
    normalizeModulePath(modulePath).split("/").toSeq.map(_.replace('-', '_'))
}
