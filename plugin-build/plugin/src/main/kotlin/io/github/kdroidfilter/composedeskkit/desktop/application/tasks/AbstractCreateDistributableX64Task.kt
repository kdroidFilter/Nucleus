/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.tasks

import io.github.kdroidfilter.composedeskkit.desktop.tasks.AbstractComposeDesktopTask
import io.github.kdroidfilter.composedeskkit.internal.utils.notNullProperty
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Launches a separate Gradle process with JAVA_HOME set to an x64 JDK (running under Rosetta)
 * to build the x64 distributable. The subprocess uses `-PcomposeDeskKit.x64Build=true` so that
 * the plugin redirects its output to a separate directory and skips universal-binary task registration.
 */
@DisableCachingByDefault(because = "Subprocess build cannot be tracked by Gradle cache")
abstract class AbstractCreateDistributableX64Task : AbstractComposeDesktopTask() {

    @get:Input
    abstract val x64JdkHome: Property<String>

    @get:Input
    abstract val gradleTaskPaths: ListProperty<String>

    @get:Internal
    abstract val projectRootDir: DirectoryProperty

    @get:OutputDirectory
    abstract val destinationDir: DirectoryProperty

    @get:Input
    val safetyTimeoutSeconds: Property<Long> = objects.notNullProperty<Long>().apply {
        set(600L)
    }

    @TaskAction
    fun execute() {
        val rootDir = projectRootDir.get().asFile
        val gradlew = rootDir.resolve(if (isWindows()) "gradlew.bat" else "gradlew")
        if (!gradlew.exists()) {
            throw GradleException("Gradle wrapper not found at ${gradlew.absolutePath}")
        }

        val tasks = gradleTaskPaths.get()

        val args = mutableListOf(
            gradlew.absolutePath,
            *tasks.toTypedArray(),
            "--no-daemon",
            "-PcomposeDeskKit.x64Build=true",
        )

        logger.lifecycle("[universalBinary] Running x64 build: ${tasks.joinToString(" ")}")
        logger.lifecycle("[universalBinary] JAVA_HOME=${x64JdkHome.get()}")

        val logFile = File.createTempFile("composedeskkit-x64-build-", ".log")
        val pb = ProcessBuilder(args)
            .directory(rootDir)
            .redirectErrorStream(true)
            .redirectOutput(logFile)
        pb.environment()["JAVA_HOME"] = x64JdkHome.get()

        val process = pb.start()
        val deadline = System.currentTimeMillis() + safetyTimeoutSeconds.get() * 1000
        while (process.isAlive && System.currentTimeMillis() < deadline) {
            Thread.sleep(1000)
        }
        if (process.isAlive) {
            process.destroyForcibly()
            throw GradleException("x64 build timed out after ${safetyTimeoutSeconds.get()}s")
        }

        val exitCode = process.waitFor()
        val output = logFile.readText()
        if (output.isNotBlank()) {
            val display = if (output.length > 5000) "...\n" + output.takeLast(5000) else output
            logger.lifecycle("[universalBinary] x64 build output (exit $exitCode):\n$display")
        }
        logFile.delete()

        if (exitCode != 0) {
            throw GradleException("x64 distributable build failed with exit code $exitCode")
        }

        val destDir = destinationDir.get().asFile
        if (!destDir.exists() || destDir.listFiles()?.isEmpty() != false) {
            throw GradleException("x64 distributable not found at ${destDir.absolutePath}")
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
}
