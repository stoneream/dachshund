package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.model.{GeneratedArtifacts, GeneratedFile}
import io.github.stoneream.dachshund.codegen.schema.{TblsColumn, TblsSchema, TblsTable}
import io.github.stoneream.dachshund.codegen.util.Naming

import java.nio.file.Path

object GenerateDbMetadata {
  def render(
      tableName: String,
      schemaJson: Path,
      repositoryRoot: Path
  ): GeneratedArtifacts = {
    val table =
      TblsSchema
        .load(schemaJson)
        .flatMap(_.findTable(tableName))
        .fold(message => throw new IllegalArgumentException(message), identity)

    renderTable(table, repositoryRoot)
  }

  def renderTable(
      table: TblsTable,
      repositoryRoot: Path
  ): GeneratedArtifacts = {
    val tableBaseName = Naming.toUpperCamelFromSnake(table.name)
    val objectName = s"${tableBaseName}Table"
    val rowName = s"${tableBaseName}DbRow"
    val basePath = repositoryRoot
      .resolve("application/src/main/scala/io/github/stoneream/dachshund/infra/db/generated")
      .normalize
    val columns = table.columns.map(toScalaColumn)
    val javaTimeImport = buildJavaTimeImport(columns)

    GeneratedArtifacts(
      files = Seq(
        GeneratedFile(basePath.resolve(s"$rowName.scala"), row(rowName, columns, javaTimeImport)),
        GeneratedFile(basePath.resolve(s"$objectName.scala"), tableMetadata(table, objectName, rowName, columns))
      )
    )
  }

  private def row(
      rowName: String,
      columns: Seq[ScalaColumn],
      javaTimeImport: String
  ): String =
    // language=scala 3
    s"""package io.github.stoneream.dachshund.infra.db.generated
       |
       |$javaTimeImport${if (javaTimeImport.nonEmpty) "\n" else ""}
       |final case class $rowName(
       |${columns.map(column => s"    ${column.fieldName}: ${column.scalaType}").mkString(",\n")}
       |)
       |""".stripMargin

  private def tableMetadata(
      table: TblsTable,
      objectName: String,
      rowName: String,
      columns: Seq[ScalaColumn]
  ): String =
    // language=scala 3
    s"""package io.github.stoneream.dachshund.infra.db.generated
       |
       |import scalikejdbc.WrappedResultSet
       |
       |object $objectName {
       |  val Name = "${table.name}"
       |
       |  object Columns {
       |${columns.map(column => s"""    val ${column.constantName} = "${column.name}"""").mkString("\n")}
       |
       |    val All: Seq[String] = Seq(
       |${columns.map(column => s"      ${column.constantName}").mkString(",\n")}
       |    )
       |  }
       |
       |  val InsertAuditColumnNames: Seq[String] = ${auditColumns(columns, Seq("created_at", "created_user")).mkString("Seq(", ", ", ")")}
       |  val UpdateAuditColumnNames: Seq[String] = ${auditColumns(columns, Seq("updated_at", "updated_user", "lock_version")).mkString("Seq(", ", ", ")")}
       |  val DeleteAuditColumnNames: Seq[String] = ${auditColumns(columns, Seq("deleted_at", "deleted_user", "deleted")).mkString("Seq(", ", ", ")")}
       |
       |  def map(rs: WrappedResultSet): $rowName =
       |    $rowName(
       |${columns.map(column => s"      ${column.fieldName} = ${column.mapper}").mkString(",\n")}
       |    )
       |}
       |""".stripMargin

  private def toScalaColumn(column: TblsColumn): ScalaColumn = {
    val fieldName = escapeIdentifier(toLowerCamelFromSnake(column.name))
    val baseType = scalaBaseType(column.columnType)
    val scalaType = if (column.nullable) s"Option[$baseType]" else baseType

    ScalaColumn(
      name = column.name,
      constantName = Naming.toUpperCamelFromSnake(column.name),
      fieldName = fieldName,
      scalaType = scalaType,
      baseType = baseType,
      mapper = mapper(column.name, baseType, column.nullable)
    )
  }

  private def scalaBaseType(columnType: String): String = {
    val normalized = columnType.toLowerCase

    if (normalized.startsWith("bigint") || normalized.startsWith("serial")) {
      "Long"
    } else if (normalized.startsWith("int") || normalized.startsWith("smallint") || normalized.startsWith("mediumint") || normalized.startsWith("tinyint")) {
      "Int"
    } else if (
      normalized.startsWith("varchar") || normalized.startsWith("char") || normalized.contains("text") || normalized.startsWith("enum") || normalized
        .startsWith("json")
    ) {
      "String"
    } else if (normalized.startsWith("varbinary") || normalized.startsWith("binary") || normalized.contains("blob")) {
      "Array[Byte]"
    } else if (normalized.startsWith("datetime") || normalized.startsWith("timestamp")) {
      "LocalDateTime"
    } else if (normalized.startsWith("date")) {
      "LocalDate"
    } else if (normalized.startsWith("decimal") || normalized.startsWith("numeric")) {
      "BigDecimal"
    } else if (normalized.startsWith("double") || normalized.startsWith("float")) {
      "Double"
    } else {
      "String"
    }
  }

  private def mapper(
      columnName: String,
      baseType: String,
      nullable: Boolean
  ): String = {
    val accessor = baseType match {
      case "Long" => "long"
      case "Int" => "int"
      case "String" => "string"
      case "Array[Byte]" => "bytes"
      case "LocalDateTime" => "localDateTime"
      case "LocalDate" => "localDate"
      case "BigDecimal" => "bigDecimal"
      case "Double" => "double"
      case _ => "string"
    }
    val methodName = if (nullable) s"${accessor}Opt" else accessor
    s"rs.$methodName(Columns.${Naming.toUpperCamelFromSnake(columnName)})"
  }

  private def toLowerCamelFromSnake(name: String): String =
    Naming.toLowerCamel(Naming.toUpperCamelFromSnake(name))

  private def escapeIdentifier(name: String): String = {
    val reserved = Set(
      "abstract",
      "case",
      "catch",
      "class",
      "def",
      "do",
      "else",
      "enum",
      "export",
      "extends",
      "false",
      "final",
      "finally",
      "for",
      "forSome",
      "given",
      "if",
      "implicit",
      "import",
      "lazy",
      "match",
      "new",
      "null",
      "object",
      "override",
      "package",
      "private",
      "protected",
      "return",
      "sealed",
      "super",
      "then",
      "this",
      "throw",
      "trait",
      "try",
      "true",
      "type",
      "val",
      "var",
      "while",
      "with",
      "yield"
    )
    if (reserved.contains(name)) s"`$name`" else name
  }

  private def buildJavaTimeImport(columns: Seq[ScalaColumn]): String = {
    val timeTypes = columns.map(_.baseType).filter(baseType => baseType == "LocalDate" || baseType == "LocalDateTime").distinct
    timeTypes match {
      case Seq() => ""
      case Seq(singleType) => s"import java.time.$singleType"
      case _ => "import java.time.{LocalDate, LocalDateTime}"
    }
  }

  private def auditColumns(
      columns: Seq[ScalaColumn],
      auditColumnNames: Seq[String]
  ): Seq[String] = {
    val constantsByName = columns.map(column => column.name -> column.constantName).toMap
    auditColumnNames.flatMap(columnName => constantsByName.get(columnName).map(constantName => s"Columns.$constantName"))
  }

  private final case class ScalaColumn(
      name: String,
      constantName: String,
      fieldName: String,
      scalaType: String,
      baseType: String,
      mapper: String
  )
}
