package io.github.stoneream.dachshund.handler.lib

import play.api.mvc.Call

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Properties
import scala.util.Using

object StaticAssetUrl {
  private val ResourceName = "/dachshund-build.properties"
  private val AssetVersionPropertyName = "asset.version"
  private val DefaultAssetVersion = "dev"

  private lazy val assetVersion: String = {
    val properties = new Properties()
    val versionOpt = for {
      inputStream <- Option(getClass.getResourceAsStream(ResourceName))
      version <- Using(inputStream) { input =>
        properties.load(input)
        Option(properties.getProperty(AssetVersionPropertyName)).map(_.trim).filter(_.nonEmpty)
      }.toOption.flatten
    } yield version

    versionOpt.getOrElse(DefaultAssetVersion)
  }

  private lazy val encodedAssetVersion: String =
    URLEncoder.encode(assetVersion, StandardCharsets.UTF_8)

  def versioned(call: Call): String = {
    val separator = if (call.url.contains("?")) "&" else "?"
    s"${call.url}${separator}v=$encodedAssetVersion"
  }
}
