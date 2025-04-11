package cloud.app.vvf.tasks

import cloud.app.vvf.getVvfExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.r8.ClassFileProviderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.Arrays
import java.util.stream.Collectors

abstract class CompileDexTask : DefaultTask() {
  @InputFiles
  @SkipWhenEmpty
  @IgnoreEmptyDirectories
  val input: ConfigurableFileCollection = project.objects.fileCollection()

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @get:OutputFile
  abstract val pluginClassFile: RegularFileProperty

  @TaskAction
  fun compileDex() {
    val android = project.extensions.getByName("android") as BaseExtension
    val minSdk = android.defaultConfig.minSdk ?: 21
    val dexOutputDir = outputFile.get().asFile.parentFile
    var vvfLibVersion: String? = null // Store version from VVFBuildConfig

    val desugarBootclasspathFactory = ClassFileProviderFactory(android.bootClasspath.map(File::toPath))
    val desugarClasspathFactory = ClassFileProviderFactory(listOf())

    try {
      val dexBuilder = DexArchiveBuilder.createD8DexBuilder(
        DexParameters(
          minSdkVersion = minSdk,
          debuggable = true,
          dexPerClass = false,
          withDesugaring = minSdk >= 24,
          desugarBootclasspath = desugarBootclasspathFactory,
          desugarClasspath = desugarClasspathFactory,
          coreLibDesugarConfig = null,
          messageReceiver = MessageReceiverImpl(
            ErrorFormatMode.HUMAN_READABLE,
            LoggerFactory.getLogger(CompileDexTask::class.java)
          ),
          enableApiModeling = false
        )
      )

      val fileStreams = input.map { input ->
        val path = input.toPath()
        if (Files.exists(path)) {
          ClassFileInputs.fromPath(path).use { it.entries { _, _ -> true } }
        } else {
          println("Path does not exist: $path")
          null
        }
      }.toTypedArray()

      Arrays.stream(fileStreams).flatMap { it }
        .use { classesInput ->
          val files = classesInput.collect(Collectors.toList())
          println("files.size= = ${files.size}")
          dexBuilder.convert(
            files.stream(),
            dexOutputDir.toPath(),
            null,
          )

          for (file in files) {
            val reader = ClassReader(file.readAllBytes())
            val classNode = ClassNode()
            reader.accept(classNode, 0)

            // Check for VVFBuildConfig to extract LIB_VERSION
            if (classNode.name == "cloud/app/vvf/common/VVFBuildConfig") {
              vvfLibVersion = getVersionFromBuildConfig(classNode)
              println("Found VVFBuildConfig.LIB_VERSION = $vvfLibVersion")
            }

            // Process VVFExtension annotations
            for (annotation in classNode.visibleAnnotations.orEmpty() + classNode.invisibleAnnotations.orEmpty()) {
              if (annotation.desc == "Lcloud/app/vvf/common/VVFExtension;") {
                val vvfExtension = project.extensions.getVvfExtension()

                require(vvfExtension.pluginClassName == null) {
                  "Only 1 active plugin class per project is supported"
                }

                vvfExtension.pluginClassName = classNode.name.replace('/', '.').also {
                  pluginClassFile.asFile.orNull?.writeText(it)
                }

                // Use version from VVFBuildConfig if available, else fallback
                vvfExtension.vffLibVersion = vvfLibVersion ?: getVvfLibraryVersion()

                val interfaces = classNode.interfaces

                println("vvfExtension.pluginClassName = ${vvfExtension.pluginClassName}")
                println("vvfExtension.vffLibVersion = ${vvfExtension.vffLibVersion}")
              }
            }
          }
        }

    } finally {
      desugarBootclasspathFactory.close()
      desugarClasspathFactory.close()
    }

    logger.lifecycle("Compiled dex to ${outputFile.get()}")
  }

  private fun getVersionFromBuildConfig(classNode: ClassNode): String? {
    // Look for the LIB_VERSION field
    val field = classNode.fields?.find { field ->
      field is FieldNode && field.name == "LIB_VERSION" && field.desc == "Ljava/lang/String;"
    }

    // Extract the constant value if it's a static final String
    return field?.value as? String
  }

  private fun getVvfLibraryVersion(): String {
    val vvfDependency = "cloud.app.vvf.common:common"
    return try {
      val configuration = project.configurations.getByName("implementation")
      val dependency = configuration.resolvedConfiguration.resolvedArtifacts
        .find { it.moduleVersion.id.toString().startsWith(vvfDependency) }
      dependency?.moduleVersion?.id?.version ?: "Unknown"
    } catch (e: Exception) {
      logger.error("Failed to resolve VVF library version for $vvfDependency", e)
      "Unknown"
    }
  }
}
