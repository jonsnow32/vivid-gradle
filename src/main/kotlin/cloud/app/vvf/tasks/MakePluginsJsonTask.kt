package cloud.app.vvf.tasks

import cloud.app.vvf.entities.VvfMetadata
import cloud.app.vvf.entities.makeVVFMetadata
import cloud.app.vvf.findVvfExtension
import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.LinkedList

abstract class MakePluginsJsonTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun makePluginsJson() {
        val lst = LinkedList<VvfMetadata>()

        for (subproject in project.allprojects) {
            subproject.extensions.findVvfExtension() ?: continue
            lst.add(subproject.makeVVFMetadata())
        }

        outputFile.asFile.get().writeText(
            JsonBuilder(
                lst,
                JsonGenerator.Options()
                    .excludeNulls()
                    .build()
            ).toPrettyString()
        )

        logger.lifecycle("Created ${outputFile.asFile.get()}")
    }
}
