package cloud.app.vvf.entities

import cloud.app.vvf.getVvfExtension
import org.gradle.api.Project
import org.gradle.internal.impldep.kotlinx.serialization.SerialName

enum class ExtensionType(val feature: String) {
  DATABASE("database"), STREAM("stream"), SUBTITLE("subtitle");
}

open class VvfMetadata(
  val className: String,
  val url: String,
  val types: List<String>?,
  val version: String,
  val iconUrl: String?,
  val name: String,
  val description: String?,
  val author: List<String>,
  val repoUrl: String?,
  val fileSize: Long?,
  val status: Int,
  val vvfLibVersion: String?
)

fun Project.makeVVFMetadata(): VvfMetadata {
  val extension = this.extensions.getVvfExtension()

  val version = this.version.toString().toIntOrNull(10)
  if (version == null) {
    logger.warn("'${project.version}' is not a valid version. Use an integer.")
  }
  val repo = extension.repository

  return VvfMetadata(
    url = repo?.getDownloadLink("${this.name}.vvf") ?: "",
    types = extension.types,
    className = extension.pluginClassName!!,
    vvfLibVersion = extension.vffLibVersion,
    version = version.toString(),
    iconUrl = extension.iconUrl,
    name = this.name,
    description = extension.description,
    author = extension.authors,
    repoUrl = repo?.url,
    fileSize = extension.fileSize,
    status = extension.status,
  )
}
