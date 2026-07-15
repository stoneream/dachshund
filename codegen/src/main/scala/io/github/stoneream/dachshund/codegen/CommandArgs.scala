package io.github.stoneream.dachshund.codegen

import io.github.stoneream.dachshund.codegen.util.Naming

import java.nio.file.Path

final case class CommandArgs(
    usecase: Option[String] = None,
    step: Option[String] = None,
    endpoint: Option[String] = None,
    daemon: Option[String] = None,
    daemonHandler: Option[String] = None,
    dbMetadata: Boolean = false,
    dbAccessor: Option[String] = None,
    name: Option[String] = None,
    table: Option[String] = None,
    reader: Option[String] = None,
    writer: Option[String] = None,
    schemaJson: Path = Path.of("tbls/schema/schema.json"),
    repositoryRoot: Path = Path.of("."),
    force: Boolean = false,
    dryRun: Boolean = false
) {
  def resolve: Either[String, GenerationRequest] = {
    val selectedTargets =
      Seq(usecase, step, endpoint, daemon, daemonHandler, dbAccessor).count(_.isDefined) + (if (dbMetadata) 1 else 0)

    if (selectedTargets != 1) {
      Left("生成対象は --usecase / --step / --endpoint / --daemon / --daemon-handler / --db-metadata / --db-accessor のいずれか 1 つだけ指定してください")
    } else {
      resolveSelectedTarget
    }
  }

  private def resolveSelectedTarget: Either[String, GenerationRequest] =
    usecase
      .map(path => requireNamedPath(path, "--usecase").map(GenerationRequest.UseCase.apply))
      .orElse(step.map(path => requireNamedPath(path, "--step").map(GenerationRequest.Step.apply)))
      .orElse(endpoint.map(path => requireNamedPath(path, "--endpoint").map(GenerationRequest.Endpoint.apply)))
      .orElse(daemon.map(path => requireNamedPath(path, "--daemon").map(GenerationRequest.Daemon.apply)))
      .orElse(
        daemonHandler.map(path => requireNamedPath(path, "--daemon-handler").map(GenerationRequest.DaemonHandler.apply))
      )
      .orElse(if (dbMetadata) Some(resolveDbMetadata) else None)
      .orElse(dbAccessor.map(resolveDbAccessor))
      .getOrElse(Left("生成対象が指定されていません"))

  private def requireNamedPath(
      path: String,
      optionName: String
  ): Either[String, NamedPathRequest] =
    for {
      normalizedPath <- validateModulePath(path, optionName)
      normalizedName <- requireName
    } yield NamedPathRequest(
      modulePath = normalizedPath,
      name = normalizedName
    )

  private def resolveDbMetadata: Either[String, GenerationRequest.DbMetadata] =
    table match {
      case Some(value) =>
        Naming
          .validateDbName(value, "--table")
          .map(_ =>
            GenerationRequest.DbMetadata(
              table = value,
              schemaJson = repositoryRoot.resolve(schemaJson).normalize
            )
          )
      case None => Left("--db-metadata には --table が必須です")
    }

  private def resolveDbAccessor(path: String): Either[String, GenerationRequest.DbAccessor] =
    for {
      normalizedPath <- validateModulePath(path, "--db-accessor")
      readerName <- requireUpperCamel(reader, "--reader")
      writerName <- requireUpperCamel(writer, "--writer")
    } yield GenerationRequest.DbAccessor(
      modulePath = normalizedPath,
      reader = readerName,
      writer = writerName
    )

  private def requireName: Either[String, String] =
    requireUpperCamel(name, "--name")

  private def requireUpperCamel(value: Option[String], optionName: String): Either[String, String] =
    value match {
      case Some(rawValue) => Naming.validateUpperCamel(rawValue, optionName).map(_ => rawValue)
      case None => Left(s"$optionName は必須です")
    }

  private def validateModulePath(value: String, optionName: String): Either[String, String] = {
    val normalized = Naming.normalizeModulePath(value)
    Naming.validateModulePath(normalized, optionName).map(_ => normalized)
  }
}

final case class NamedPathRequest(
    modulePath: String,
    name: String
)

enum GenerationRequest {
  case UseCase(request: NamedPathRequest)
  case Step(request: NamedPathRequest)
  case Endpoint(request: NamedPathRequest)
  case Daemon(request: NamedPathRequest)
  case DaemonHandler(request: NamedPathRequest)
  case DbMetadata(table: String, schemaJson: Path)
  case DbAccessor(modulePath: String, reader: String, writer: String)
}
