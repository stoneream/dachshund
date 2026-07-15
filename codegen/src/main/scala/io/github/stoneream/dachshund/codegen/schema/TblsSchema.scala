package io.github.stoneream.dachshund.codegen.schema

import io.circe.Json
import io.circe.parser.parse

import java.nio.file.{Files, Path}

final case class TblsSchema(
    tables: Seq[TblsTable]
) {
  def findTable(tableName: String): Either[String, TblsTable] =
    tables
      .find(_.name == tableName)
      .toRight(s"table が schema JSON に存在しません: $tableName")
}

final case class TblsTable(
    name: String,
    columns: Seq[TblsColumn]
)

final case class TblsColumn(
    name: String,
    columnType: String,
    nullable: Boolean
)

object TblsSchema {
  def load(path: Path): Either[String, TblsSchema] =
    if (!Files.exists(path)) {
      Left(s"schema JSON が存在しません: ${path.toString}")
    } else {
      parse(Files.readString(path)).left.map(_.message).flatMap(decode)
    }

  private def decode(json: Json): Either[String, TblsSchema] =
    json.hcursor.downField("tables").focus match {
      case Some(value) if value.isNull =>
        Left("schema JSON の tables が null です。tbls schema を生成してから再実行してください")
      case Some(value) =>
        value.asArray match {
          case Some(tablesJson) => sequence(tablesJson.map(decodeTable)).map(TblsSchema.apply)
          case None => Left("schema JSON の tables が配列ではありません")
        }
      case None => Left("schema JSON に tables がありません")
    }

  private def decodeTable(json: Json): Either[String, TblsTable] = {
    val cursor = json.hcursor
    for {
      name <- cursor.get[String]("name").left.map(_.message)
      columnsJson <- cursor.downField("columns").as[Vector[Json]].left.map(_.message)
      columns <- sequence(columnsJson.map(decodeColumn))
    } yield TblsTable(
      name = name,
      columns = columns
    )
  }

  private def decodeColumn(json: Json): Either[String, TblsColumn] = {
    val cursor = json.hcursor
    for {
      name <- cursor.get[String]("name").left.map(_.message)
      columnType <- cursor.get[String]("type").left.map(_.message)
      nullable <- cursor.get[Boolean]("nullable").left.map(_.message)
    } yield TblsColumn(
      name = name,
      columnType = columnType,
      nullable = nullable
    )
  }

  private def sequence[A](items: Vector[Either[String, A]]): Either[String, Vector[A]] =
    items.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (result, item) =>
      for {
        values <- result
        value <- item
      } yield values :+ value
    }
}
