package cloud.app.vvf.utils

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.service.ServiceRegistry
import java.io.File
import java.io.FileOutputStream
import java.net.URL


fun createProgressLogger(project: Project, loggerCategory: String): ProgressLogger {
    return createProgressLogger((project as ProjectInternal).services, loggerCategory)
}

fun createProgressLogger(services: ServiceRegistry, loggerCategory: String): ProgressLogger {
    val progressLoggerFactory = services.get(ProgressLoggerFactory::class.java)
    return progressLoggerFactory.newOperation(loggerCategory).also { it.description = loggerCategory }
}

fun URL.download(file: File, progressLogger: ProgressLogger) {
    progressLogger.started()
    try {
        val tempFile = File.createTempFile(file.name, ".part", file.parentFile)
        tempFile.deleteOnExit()

        val connection = this.openConnection()
        val size = connection.contentLengthLong
        val sizeText = toLengthText(size)

        connection.getInputStream().use { inputStream ->
            var finished = false
            var processedBytes: Long = 0
            try {
                FileOutputStream(tempFile).use { os ->
                    val buf = ByteArray(1024 * 10)
                    var read: Int
                    while (inputStream.read(buf).also { read = it } >= 0) {
                        os.write(buf, 0, read)
                        processedBytes += read
                        progressLogger.progress("Downloading ${toLengthText(processedBytes)}/$sizeText")
                    }
                    os.flush()
                    finished = true
                }
            } finally {
                if (finished) {
                    tempFile.renameTo(file)
                } else {
                    tempFile.delete()
                }
            }
        }
    } finally {
        progressLogger.completed()
    }
}

private fun toLengthText(bytes: Long): String {
    return if (bytes < 1024) {
        "$bytes B"
    } else if (bytes < 1024 * 1024) {
        (bytes / 1024).toString() + " KB"
    } else if (bytes < 1024 * 1024 * 1024) {
        String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    } else {
        String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
