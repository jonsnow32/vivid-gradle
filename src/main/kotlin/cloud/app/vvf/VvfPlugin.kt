package cloud.app.vvf


import cloud.app.vvf.entities.makeVVFMetadata
import cloud.app.vvf.tasks.CleanCacheTask
import cloud.app.vvf.tasks.CompileDexTask
import cloud.app.vvf.tasks.CompileResourcesTask
import cloud.app.vvf.tasks.DeployWithAdbTask
import cloud.app.vvf.tasks.GenSourcesTask
import cloud.app.vvf.tasks.MakePluginsJsonTask
import cloud.app.vvf.tasks.UploadSourceTask
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.tasks.ProcessLibraryManifest
import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

const val TASK_GROUP = "vvf"
class VvfPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.create("vvfExtension", VvfExtension::class.java, target)
    registerTasks(target)
  }

  private fun registerTasks(project: Project) {
    val extension = project.extensions.getVvfExtension()
    val intermediates = project.layout.buildDirectory.dir("intermediates").get().asFile

    if (project.rootProject.tasks.findByName("makePluginsJson") == null) {
      project.rootProject.tasks.register("makePluginsJson", MakePluginsJsonTask::class.java) {
        it.group = TASK_GROUP

        it.outputs.upToDateWhen { false }

        it.outputFile.set(it.project.layout.buildDirectory.dir("plugins.json").get().asFile)
      }
    }

    project.tasks.register("genSources", GenSourcesTask::class.java) {
      it.group = TASK_GROUP
    }


    val pluginClassFile = intermediates.resolve("pluginClass")

    val compileDex = project.tasks.register("compileDex", CompileDexTask::class.java) {
      it.group = TASK_GROUP

      it.pluginClassFile.set(pluginClassFile)

      val kotlinTask = project.tasks.findByName("compileDebugKotlin") as KotlinCompile?
      if (kotlinTask != null) {
        it.dependsOn(kotlinTask)
        it.input.from(kotlinTask.destinationDirectory)
      }

      val javacTask = project.tasks.findByName("compileDebugJavaWithJavac") as AbstractCompile?
      if (javacTask != null) {
        it.dependsOn(javacTask)
        it.input.from(javacTask.destinationDirectory)
      }

      it.outputFile.set(intermediates.resolve("classes.dex"))
    }

    val compileResources =
      project.tasks.register("compileResources", CompileResourcesTask::class.java) { it ->
        it.group = TASK_GROUP

        val processManifestTask =
          project.tasks.getByName("processDebugManifest") as ProcessLibraryManifest
        it.dependsOn(processManifestTask)

        val android = project.extensions.getByName("android") as BaseExtension
        it.input.set(android.sourceSets.getByName("main").res.srcDirs.single())
        it.manifestFile.set(processManifestTask.manifestOutputFile)

        it.outputFile.set(intermediates.resolve("res.apk"))

        it.doLast { _ ->
          val resApkFile = it.outputFile.asFile.get()

          if (resApkFile.exists()) {
            project.tasks.named("make", AbstractCopyTask::class.java) {
              it.from(project.zipTree(resApkFile)) { copySpec ->
                copySpec.exclude("AndroidManifest.xml")
              }
            }
          }
        }
      }


    project.afterEvaluate {
      val make = project.tasks.register("make", Zip::class.java) {
        val compileDexTask = compileDex.get()
        it.dependsOn(compileDexTask)

        val metadataFile = intermediates.resolve("metadata.json")
        it.from(metadataFile)
        it.doFirst {
          if (extension.pluginClassName == null) {
            if (pluginClassFile.exists()) {
              extension.pluginClassName = pluginClassFile.readText()
            }
          }

          metadataFile.writeText(
            JsonBuilder(
              project.makeVVFMetadata(),
              JsonGenerator.Options()
                .excludeNulls()
                .build()
            ).toString()
          )
        }

        it.from(compileDexTask.outputFile)

        val zip = it as Zip
        if (extension.requiresResources) {
          zip.dependsOn(compileResources.get())
        }
        zip.isPreserveFileTimestamps = false
        zip.archiveBaseName.set(project.name)
        zip.archiveExtension.set("vvf")
        zip.archiveVersion.set("")
        zip.destinationDirectory.set(project.layout.buildDirectory)

        it.doLast { task ->
          extension.fileSize = task.outputs.files.singleFile.length()
          task.logger.lifecycle("Made VVF package at ${task.outputs.files.singleFile}")
        }
      }
      project.rootProject.tasks.getByName("makePluginsJson").dependsOn(make)

      project.tasks.register("uploadSource", UploadSourceTask::class.java) {
        it.group = TASK_GROUP
        it.dependsOn(make)
        it.input.set(make.get().outputs.files.singleFile)
      }
    }

    project.tasks.register("cleanCache", CleanCacheTask::class.java) {
      it.group = TASK_GROUP
    }

    project.tasks.register("deployWithAdb", DeployWithAdbTask::class.java) {
      it.group = TASK_GROUP
      it.dependsOn("make")
    }
  }

}

