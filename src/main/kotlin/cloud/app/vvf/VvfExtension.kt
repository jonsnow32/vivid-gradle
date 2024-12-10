package cloud.app.vvf

import cloud.app.vvf.entities.VvfMetadata
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class VvfExtension @Inject constructor(val project: Project) {
  internal var fileSize: Long? = null
  internal var pluginClassName: String? = null

  var requiresResources = false
  var description: String? = null
  var authors = listOf<String>()
  var status = 3
  var types: List<String>? = null
  var iconUrl: String? = null
  var isPreRelease = false
  var repository: Repo? = null
    internal set


  private fun setRepo(user: String, repo: String, type: String, accessToken: String?) {
    val gitHash = execute("git", "rev-parse", "HEAD").take(7)
    repository = when {
      type == "github" -> Repo(
        user,
        repo,
        "https://github.com/${user}/${repo}",
        "https://github.com/${user}/${repo}/releases/download/${gitHash}/%filename%",
        accessToken
      )
      else -> throw IllegalArgumentException("Unknown type ${type}. Use github, gitlab, gitlab-<domain> or gitea-<domain> or set repository via setRepo(user, repo, url, rawLinkFormat)")
    }
  }


  fun setRepo(url: String, accessToken: String?) {
    var type: String? = null
    val split = when {
      url.startsWith("https://github.com") -> {
        type = "github"
        url
          .removePrefix("https://")
          .removePrefix("github.com")
      }

      else -> throw IllegalArgumentException("Unknown domain, please set repository via setRepo(user, repo, type)")
    }
      .removePrefix("/")
      .removeSuffix("/")
      .split("/")

    setRepo(split[0], split[1], type, accessToken)
  }

  private fun execute(vararg command: String): String {
    val outputStream = ByteArrayOutputStream()
    project.exec { spec: ExecSpec ->
      spec.commandLine(*command)
      spec.standardOutput = outputStream
      spec.isIgnoreExitValue = true
    }
    return outputStream.toString().trim()
  }

  class Repo(val user: String, val repo: String, val url: String, val downloadLink: String, val accessToken: String? = null) {
    fun getDownloadLink(fileName: String): String {
      return downloadLink.replace("%filename%", fileName)
    }
  }
}




fun ExtensionContainer.getVvfExtension(): VvfExtension {
  return getByName("vvfExtension") as VvfExtension
}

fun ExtensionContainer.findVvfExtension(): VvfExtension? {
  return findByName("vvfExtension") as? VvfExtension
}
