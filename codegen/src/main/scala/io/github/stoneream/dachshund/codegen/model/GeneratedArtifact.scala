package io.github.stoneream.dachshund.codegen.model

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

final case class GeneratedFile(
    path: Path,
    content: String
)

final case class GeneratedSnippet(
    title: String,
    content: String
)

final case class GeneratedArtifacts(
    files: Seq[GeneratedFile],
    snippets: Seq[GeneratedSnippet] = Seq.empty
) {
  def ++(other: GeneratedArtifacts): GeneratedArtifacts =
    GeneratedArtifacts(
      files = files ++ other.files,
      snippets = snippets ++ other.snippets
    )
}

object GeneratedArtifacts {
  val Empty: GeneratedArtifacts = GeneratedArtifacts(Seq.empty)
}

object GeneratedArtifactWriter {
  def write(
      artifacts: GeneratedArtifacts,
      force: Boolean,
      dryRun: Boolean
  ): Unit = {
    val existingFiles = artifacts.files.filter(file => Files.exists(file.path))

    if (existingFiles.nonEmpty && !force && !dryRun) {
      val paths = existingFiles.map(_.path.toString).mkString(System.lineSeparator())
      throw new IllegalStateException(
        s"既存ファイルがあるため生成を中止しました。上書きする場合は --force を指定してください。${System.lineSeparator()}$paths"
      )
    }

    if (dryRun) {
      printDryRun(artifacts, existingFiles)
    } else {
      artifacts.files.foreach(writeFile)
      artifacts.snippets.foreach(printSnippet)
    }
  }

  private def writeFile(file: GeneratedFile): Unit = {
    Option(file.path.getParent).foreach(parent => Files.createDirectories(parent))
    Files.writeString(
      file.path,
      ensureFinalNewline(file.content),
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    println(s"生成しました: ${file.path.toString}")
  }

  private def printDryRun(
      artifacts: GeneratedArtifacts,
      existingFiles: Seq[GeneratedFile]
  ): Unit = {
    val existingPaths = existingFiles.map(_.path).toSet
    artifacts.files.foreach { file =>
      val status = if (existingPaths.contains(file.path)) "exists" else "new"
      println(s"[dry-run][$status] ${file.path.toString}")
    }
    artifacts.snippets.foreach(printSnippet)
  }

  private def printSnippet(snippet: GeneratedSnippet): Unit = {
    println(s"[snippet] ${snippet.title}")
    println(snippet.content)
  }

  private def ensureFinalNewline(content: String): String =
    if (content.endsWith(System.lineSeparator()) || content.endsWith("\n")) content else s"$content${System.lineSeparator()}"
}
