package cloud.app.vvf.tasks

import cloud.app.vvf.getVvfExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class GenSourcesTask : DefaultTask() {
    @TaskAction
    fun genSources() {
        val extension = project.extensions.getVvfExtension()

      TODO()
//        val apkinfo = extension.apkinfo!!
//
//        val sourcesJarFile = apkinfo.cache.resolve("vvf-sources.jar")
//
//        val url = URL("${apkinfo.urlPrefix}/app-sources.jar")
//
//        url.download(sourcesJarFile, createProgressLogger(project, "Download sources"))
    }
}
