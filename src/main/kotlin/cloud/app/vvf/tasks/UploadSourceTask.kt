package cloud.app.vvf.tasks

import cloud.app.vvf.getVvfExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

abstract class UploadSourceTask : DefaultTask() {

  @get:InputFile
  abstract val input: DirectoryProperty

  @TaskAction
  fun uploadSource() {

    val extension = project.extensions.getVvfExtension()
    val repository = extension.repository ?: error("Missing repository")
    val token = repository.accessToken ?: error("Missing git token")
    val githubApiUrl = "https://api.github.com/repos/${repository.user}/${repository.repo}/releases"
    val isPreRelease = extension.isPreRelease

    // 1. get build file  file
    val outputFile = input.get().asFile
    val gitHash = execute("git", "rev-parse", "HEAD").take(7)
    val gitCount = execute("git", "rev-list", "--count", "HEAD").toInt()
    val gitMessage = execute("git", "log", "-1", "--pretty=%B").trim()
    val verCode = gitCount
    val verName = gitHash

    // 2. Get or create a release
    val releaseId = getOrCreateRelease(
      githubApiUrl,
      token,
      verName, // Use verName as tag
      "Release $verName", // Use verName in release name
      gitMessage, // Use gitMessage as release body
      isPreRelease // Set isPreRelease to false
    )

    // 3. Get the release upload URL
    val uploadUrl = getReleaseUploadUrl(githubApiUrl, releaseId, token)

    // 4. Upload the asset
    uploadReleaseAsset(outputFile, uploadUrl, token)
  }

  fun getOrCreateRelease(
    apiUrl: String,
    token: String,
    tag: String,
    name: String,
    body: String,
    isPreRelease: Boolean
  ): String {
    // Check if a release with the given tag already exists
    val existingReleaseId = findExistingReleaseId(apiUrl, token, tag, isPreRelease)

    // If a release exists, return its ID
    if (existingReleaseId != null) {
      return existingReleaseId
    }

    // Otherwise, create a new release and return its ID
    return createGitHubRelease(apiUrl, token, tag, name, body, isPreRelease)
  }


  private fun findExistingReleaseId(
    apiUrl: String,
    token: String,
    tag: String,
    isPreRelease: Boolean
  ): String? {
    val url = URL(apiUrl) // Use the base releases API URL
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("Authorization", "token $token")

    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
      val response = connection.inputStream.bufferedReader().readText()
      val jsonArray = JsonParser.parseString(response).asJsonArray

      for (jsonElement in jsonArray) {
        val jsonObject = jsonElement.asJsonObject
        if (jsonObject.get("tag_name").asString == tag && jsonObject.get("prerelease").asBoolean == isPreRelease) {
          return jsonObject.get("id").asString
        }
      }
    }

    return null // Release not found
  }

  // Helper functions
  fun createGitHubRelease(
    apiUrl: String,
    token: String,
    tag: String,
    name: String,
    body: String,
    isPreRelease: Boolean
  ): String {
    val url = URL(apiUrl)
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Authorization", "token $token")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    println("body= ${body}")
    val payload = """
        {
            "tag_name": "$tag",
            "name": "$name",
            "body": "$body",
            "prerelease": $isPreRelease
        }
    """.trimIndent()


    OutputStreamWriter(connection.outputStream).use { it.write(payload) }

    if (connection.responseCode != HttpURLConnection.HTTP_CREATED) {
      throw RuntimeException("Failed to create release: ${connection.responseCode} - ${connection.responseMessage}")
    }

    val response = connection.inputStream.bufferedReader().readText()
    val jsonResponse = JsonParser.parseString(response).asJsonObject
    return jsonResponse.get("id").asString // Extract release ID
  }

  fun getReleaseUploadUrl(apiUrl: String, releaseId: String, token: String?): String {
    val url = URL("$apiUrl/$releaseId")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("Authorization", "token $token")

    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
      throw RuntimeException("Failed to fetch upload URL: ${connection.responseCode} - ${connection.responseMessage}")
    }

    val response = connection.inputStream.bufferedReader().readText()
    val jsonResponse = JsonParser.parseString(response).asJsonObject
    return jsonResponse.get("upload_url").asString.replace("{?name,label}", "")
  }
  fun uploadReleaseAsset(assetFile: File, uploadUrl: String, token: String, overrideExisting: Boolean = false) {
    val existingAssetUrl = if (overrideExisting) findExistingAssetUrl(uploadUrl, assetFile.name, token) else null

    if (existingAssetUrl != null) {
      // Override existing asset
      val url = URL(existingAssetUrl)
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "PATCH" // Use PATCH to update
      // ... (rest of the code for updating the asset) ...
    } else {
      // Upload new asset
      val url = URL("$uploadUrl?name=${assetFile.name}")
      val connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "POST"
      // ... (original code for uploading a new asset) ...
    }
  }

  private fun findExistingAssetUrl(uploadUrl: String, assetName: String, token: String): String? {
    val url = URL(uploadUrl.replace("{?name,label}", "")) // Remove query parameters
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("Authorization", "token $token")

    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
      val response = connection.inputStream.bufferedReader().readText()
      val jsonArray = JsonParser.parseString(response).asJsonArray

      for (jsonElement in jsonArray) {
        val jsonObject = jsonElement.asJsonObject
        if (jsonObject.get("name").asString == assetName) {
          return jsonObject.get("url").asString
        }
      }
    }

    return null // Asset not found
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
}
