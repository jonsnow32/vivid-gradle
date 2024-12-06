package cloud.app.vvf.tasks

import cloud.app.vvf.getVvfExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class CleanCacheTask : DefaultTask() {
    @TaskAction
    fun cleanCache() {
        val extension = project.extensions.getVvfExtension()
    }
}
