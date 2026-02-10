package io.github.kdroidfilter.composedeskkit

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

abstract class TemplateExampleTask : DefaultTask() {
    @get:Input
    abstract val messageValue: Property<String>

    @get:Input
    abstract val tagValue: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        outputFile.convention(project.layout.buildDirectory.file("template-example.txt"))
    }

    @Option(option = "message", description = "Message to write in the template output file.")
    fun setMessage(value: String) {
        messageValue.set(value)
    }

    @Option(option = "tag", description = "Tag to prefix the message with.")
    fun setTag(value: String) {
        tagValue.set(value)
    }

    @TaskAction
    fun run() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("[${tagValue.get()}] ${messageValue.get()}\n")
    }
}
