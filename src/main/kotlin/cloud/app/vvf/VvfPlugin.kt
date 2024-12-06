package cloud.app.vvf

import cloud.app.vvf.tasks.registerTasks
import org.gradle.api.Plugin
import org.gradle.api.Project

class VvfPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.create("viVidExtension", VvfExtension::class.java, target)
    registerTasks(target)
  }
}
