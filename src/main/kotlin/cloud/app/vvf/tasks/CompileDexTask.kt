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
    // Initialize resources

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
        if (Files.exists(path)) {  // Check if the path exists
          ClassFileInputs.fromPath(path).use { it.entries { _, _ -> true } }
        } else {
          println("Path does not exist: $path")  // Log the missing path
          null  // Skip non-existent paths
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

            for (annotation in classNode.visibleAnnotations.orEmpty() + classNode.invisibleAnnotations.orEmpty()) {
              if (annotation.desc == "Lcloud/app/vvf/common/VVFExtension;") {
                val vvfExtension = project.extensions.getVvfExtension()

                require(vvfExtension.pluginClassName == null) {
                  "Only 1 active plugin class per project is supported"
                }

                vvfExtension.pluginClassName = classNode.name.replace('/', '.').also { pluginClassFile.asFile.orNull?.writeText(it) }
                println("vvfExtension.pluginClassName = ${vvfExtension.pluginClassName}")
              }
            }
          }
        }

    } finally {
      // Explicitly close resources
      desugarBootclasspathFactory.close()
      desugarClasspathFactory.close()
    }

    logger.lifecycle("Compiled dex to ${outputFile.get()}")
  }
}
