package io.github.stoneream.dachshund.codegen.model

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}

class GeneratedArtifactWriterSpec extends AnyFeatureSpec with Matchers {
  Feature("生成 artifact の書き込み") {
    Scenario("write は新規ファイルを作成する") {
      val dir = tempDir()
      val path = dir.resolve("generated.txt")

      GeneratedArtifactWriter.write(
        artifacts = GeneratedArtifacts(Seq(GeneratedFile(path, "generated"))),
        force = false,
        dryRun = false
      )

      Files.readString(path) shouldBe s"generated${System.lineSeparator()}"
    }

    Scenario("force なしで既存ファイルがある場合は write に失敗する") {
      val dir = tempDir()
      val path = dir.resolve("generated.txt")
      Files.writeString(path, "existing")

      val exception = intercept[IllegalStateException] {
        GeneratedArtifactWriter.write(
          artifacts = GeneratedArtifacts(Seq(GeneratedFile(path, "generated"))),
          force = false,
          dryRun = false
        )
      }

      exception.getMessage should include("--force")
      Files.readString(path) shouldBe "existing"
    }

    Scenario("dry-run の場合はファイルを書き込まない") {
      val dir = tempDir()
      val path = dir.resolve("generated.txt")

      GeneratedArtifactWriter.write(
        artifacts = GeneratedArtifacts(Seq(GeneratedFile(path, "generated"))),
        force = false,
        dryRun = true
      )

      Files.exists(path) shouldBe false
    }
  }

  private def tempDir(): Path =
    Files.createTempDirectory("dachshund-codegen-writer")
}
