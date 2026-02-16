/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.tasks

import io.github.kdroidfilter.nucleus.desktop.application.internal.JvmRuntimeProperties
import io.github.kdroidfilter.nucleus.desktop.tasks.AbstractNucleusTask
import io.github.kdroidfilter.nucleus.internal.utils.notNullProperty
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

private const val AOT_CACHE_FILENAME = "app.aot"
private const val MIN_AOT_JDK_VERSION = 25
private const val DEFAULT_SAFETY_TIMEOUT_SECONDS = 300L

/**
 * Generates a JDK 25+ AOT cache for a Compose Desktop distributable.
 *
 * This task:
 * 1. Locates the distributable app directory (platform-specific layout)
 * 2. Provisions a java launcher in the bundled runtime (if missing)
 * 3. Parses the `.cfg` file for classpath, JVM options, and main class
 * 4. Runs the app with `-XX:AOTCacheOutput` (single-step AOT, JDK 25+)
 * 5. Injects `-XX:AOTCache=$APPDIR/app.aot` into the `.cfg` file
 *
 * The application **must** self-terminate during training by calling `System.exit(0)`.
 * Use `AotRuntime.isTraining()` from the `aot-runtime` module to detect training mode.
 */
@DisableCachingByDefault(because = "AOT cache generation depends on runtime behavior")
abstract class AbstractGenerateAotCacheTask : AbstractNucleusTask() {
    @get:InputDirectory
    abstract val distributableDir: DirectoryProperty

    @get:Internal
    val javaHome: Property<String> =
        objects.notNullProperty<String>().apply {
            set(providers.systemProperty("java.home"))
        }

    @get:InputFile
    @get:Optional
    val javaRuntimePropertiesFile: RegularFileProperty = objects.fileProperty()

    /** Safety timeout in seconds. The task will force-kill the app if it has not exited within this time. */
    @get:Input
    val safetyTimeoutSeconds: Property<Long> =
        objects.notNullProperty<Long>().apply {
            set(DEFAULT_SAFETY_TIMEOUT_SECONDS)
        }

    @TaskAction
    fun execute() {
        checkJdkVersion()

        val baseDir = distributableDir.get().asFile
        val appDir = findAppDir(baseDir)

        logger.lifecycle("[aotCache] Processing ${appDir.name}")

        val appJarDir = findAppJarDir(appDir)
        val (javaExe, _) = provisionJavaLauncher(appDir)

        val cfgFile =
            appJarDir.listFiles()?.firstOrNull { it.extension == "cfg" }
                ?: throw GradleException("No .cfg file found in $appJarDir")
        val (classpath, javaOptions, mainClass) = parseCfgFile(cfgFile, appJarDir)

        val aotCacheFile = File(appJarDir, AOT_CACHE_FILENAME)
        val success = generateAotCache(javaExe, appDir, appJarDir, classpath, javaOptions, mainClass, aotCacheFile)

        if (success) {
            injectAotCacheIntoCfg(cfgFile)
            logger.lifecycle("[aotCache] Complete: ${aotCacheFile.absolutePath} (${aotCacheFile.length() / 1024}KB)")
        } else {
            logger.warn("[aotCache] AOT cache generation failed — the app will still work without AOT optimization")
        }
    }

    private fun checkJdkVersion() {
        val propsFile = javaRuntimePropertiesFile.orNull?.asFile ?: return
        if (!propsFile.exists()) return
        val props = JvmRuntimeProperties.readFromFile(propsFile)
        if (props.majorVersion < MIN_AOT_JDK_VERSION) {
            throw GradleException(
                "AOT cache generation requires JDK $MIN_AOT_JDK_VERSION or newer, " +
                    "but the configured JDK has major version ${props.majorVersion}. " +
                    "Set enableAotCache = false or configure a JDK $MIN_AOT_JDK_VERSION+ runtime.",
            )
        }
    }

    private fun findAppDir(baseDir: File): File {
        val children =
            baseDir
                .listFiles()
                ?.filter { it.isDirectory && it.name != ".DS_Store" }
                ?: emptyList()
        return when {
            children.isEmpty() -> throw GradleException("Distributable app directory not found under $baseDir")
            children.size == 1 -> children.single()
            else -> throw GradleException(
                "Expected a single app directory under $baseDir, found: ${children.joinToString { it.name }}",
            )
        }
    }

    private fun findAppJarDir(appDir: File): File =
        listOf(
            File(appDir, "Contents/app"), // macOS
            File(appDir, "app"), // Windows
            File(appDir, "lib/app"), // Linux
        ).firstOrNull { it.exists() }
            ?: throw GradleException("app/ subdirectory not found in $appDir")

    private fun provisionJavaLauncher(appDir: File): Pair<String, Boolean> {
        val toolchainJavaExe =
            File(javaHome.get()).resolve("bin").let { binDir ->
                val exeName = if (isWindows()) "java.exe" else "java"
                binDir.resolve(exeName).absolutePath
            }

        val runtimeHome =
            listOf(
                File(appDir, "Contents/runtime/Contents/Home"), // macOS
                File(appDir, "runtime"), // Windows
                File(appDir, "lib/runtime"), // Linux
            ).firstOrNull { it.exists() }

        if (runtimeHome == null) {
            logger.warn("[aotCache] Bundled runtime not found, using toolchain java")
            return toolchainJavaExe to false
        }

        val runtimeBinDir = File(runtimeHome, "bin")
        val exeName = if (isWindows()) "java.exe" else "java"
        val provisionedJava = File(runtimeBinDir, exeName)

        if (provisionedJava.exists()) {
            return provisionedJava.absolutePath to false
        }

        runtimeBinDir.mkdirs()
        File(toolchainJavaExe).copyTo(provisionedJava, overwrite = true)
        provisionedJava.setExecutable(true)

        if (isWindows()) {
            copyWindowsDlls(File(toolchainJavaExe).parentFile, runtimeBinDir)
        }

        logger.lifecycle("[aotCache] Provisioned java launcher at ${provisionedJava.absolutePath}")
        return provisionedJava.absolutePath to true
    }

    private fun copyWindowsDlls(
        toolchainBinDir: File,
        runtimeBinDir: File,
    ) {
        val essentialDlls = setOf("jli.dll", "vcruntime140.dll", "msvcp140.dll", "ucrtbase.dll")
        toolchainBinDir
            .listFiles()
            ?.filter { it.extension.lowercase() == "dll" && it.name.lowercase() in essentialDlls }
            ?.forEach { dll ->
                val target = File(runtimeBinDir, dll.name)
                if (!target.exists()) {
                    dll.copyTo(target, overwrite = false)
                }
            }
    }

    private data class CfgParseResult(
        val classpath: String,
        val javaOptions: List<String>,
        val mainClass: String,
    )

    private fun parseCfgFile(
        cfgFile: File,
        appJarDir: File,
    ): CfgParseResult {
        val cpEntries = mutableListOf<String>()
        val javaOptions = mutableListOf<String>()
        var mainClass = ""
        var inClasspath = false
        var inJavaOptions = false

        for (line in cfgFile.readLines()) {
            val trimmed = line.trim()
            when {
                trimmed == "[JavaOptions]" -> {
                    inJavaOptions = true
                    inClasspath = false
                }
                trimmed == "[ClassPath]" -> {
                    inClasspath = true
                    inJavaOptions = false
                }
                trimmed == "[Application]" || trimmed == "[ArgOptions]" -> {
                    inClasspath = false
                    inJavaOptions = false
                }
                trimmed.startsWith("app.mainclass=") -> mainClass = trimmed.substringAfter("app.mainclass=").trim()
                trimmed.startsWith("app.classpath=") -> cpEntries += trimmed.substringAfter("app.classpath=").trim()
                trimmed.startsWith("[") -> {
                    inClasspath = false
                    inJavaOptions = false
                }
                inClasspath && trimmed.isNotEmpty() -> cpEntries += trimmed
                inJavaOptions && trimmed.isNotEmpty() -> {
                    val opt =
                        if (trimmed.startsWith("java-options=")) {
                            trimmed.substringAfter("java-options=")
                        } else {
                            trimmed
                        }
                    if (!opt.contains("AOTCache")) {
                        javaOptions += opt.replace("\$APPDIR", appJarDir.absolutePath)
                    }
                }
            }
        }

        val classpath =
            cpEntries.joinToString(File.pathSeparator) { entry ->
                File(entry.replace("\$APPDIR", appJarDir.absolutePath)).absolutePath
            }

        return CfgParseResult(classpath, javaOptions, mainClass)
    }

    private fun generateAotCache(
        javaExe: String,
        appDir: File,
        appJarDir: File,
        classpath: String,
        javaOptions: List<String>,
        mainClass: String,
        aotCacheFile: File,
    ): Boolean {
        logger.lifecycle("[aotCache] Training – waiting for the application to exit...")
        runAotCacheCreation(javaExe, appDir, classpath, javaOptions, mainClass, aotCacheFile)

        if (!aotCacheFile.exists()) {
            logger.warn("[aotCache] AOT cache file was not created at ${aotCacheFile.absolutePath}")
            return false
        }
        return true
    }

    private fun runAotCacheCreation(
        javaExe: String,
        appDir: File,
        classpath: String,
        javaOptions: List<String>,
        mainClass: String,
        aotCacheFile: File,
    ) {
        val args = mutableListOf(javaExe)
        args += "-XX:AOTCacheOutput=${aotCacheFile.absolutePath}"
        args += "-Dnucleus.aot.mode=training"
        args += "-cp"
        args += classpath
        args += javaOptions
        args += mainClass

        val logFile = File.createTempFile("nucleus-aot-", ".log")
        val processBuilder =
            ProcessBuilder(args)
                .directory(appDir)
                .redirectErrorStream(true)
                .redirectOutput(logFile)

        val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        val needsXvfb = isLinux && System.getenv("DISPLAY").isNullOrEmpty()

        var xvfbProcess: Process? = null
        if (needsXvfb) {
            val display = ":99"
            xvfbProcess =
                ProcessBuilder("Xvfb", display, "-screen", "0", "1280x1024x24")
                    .redirectErrorStream(true)
                    .start()
            Thread.sleep(1000)
            processBuilder.environment()["DISPLAY"] = display
            logger.lifecycle("[aotCache] Started Xvfb on $display")
        }

        val process = processBuilder.start()

        val deadline = System.currentTimeMillis() + safetyTimeoutSeconds.get() * 1000
        while (process.isAlive && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
        }
        if (process.isAlive) {
            logger.warn("[aotCache] App did not self-terminate within safety timeout, forcing kill")
            process.destroyForcibly()
        }

        val exitCode = process.waitFor()
        xvfbProcess?.destroyForcibly()

        val output = logFile.readText().takeLast(3000)
        if (output.isNotBlank()) {
            logger.lifecycle("[aotCache] Output (exit $exitCode):\n$output")
        }
        logFile.delete()

        // Clean up JVM crash dumps
        appDir.listFiles()?.filter { it.name.startsWith("hs_err_pid") }?.forEach { hsErr ->
            logger.lifecycle("[aotCache] JVM crash dump: ${hsErr.name}")
            // Only read text-based .log files; .mdmp files are binary minidumps
            // that can be hundreds of MB and would cause OOM with readText()
            if (hsErr.extension == "log") {
                logger.lifecycle(hsErr.readText().take(2000))
            }
            hsErr.delete()
        }
    }

    private fun injectAotCacheIntoCfg(cfgFile: File) {
        val content = cfgFile.readText()
        if (content.contains("AOTCache")) return
        val updatedContent =
            content.replace(
                "[JavaOptions]",
                "[JavaOptions]\njava-options=-XX:AOTCache=\$APPDIR/$AOT_CACHE_FILENAME",
            )
        cfgFile.writeText(updatedContent)
        logger.lifecycle("[aotCache] Injected AOTCache into ${cfgFile.name}")
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")

}
