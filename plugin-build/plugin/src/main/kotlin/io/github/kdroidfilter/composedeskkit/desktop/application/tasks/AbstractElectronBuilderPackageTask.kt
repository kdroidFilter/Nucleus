/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.tasks

import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.JvmApplicationDistributions
import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.TargetFormat
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.WindowsKitsLocator
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.electronbuilder.ElectronBuilderConfigGenerator
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.electronbuilder.ElectronBuilderInvocation
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.electronbuilder.ElectronBuilderToolManager
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.electronbuilder.NodeJsDetector
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.updateExecutableTypeInAppImage
import io.github.kdroidfilter.composedeskkit.desktop.tasks.AbstractComposeDesktopTask
import io.github.kdroidfilter.composedeskkit.internal.utils.Arch
import io.github.kdroidfilter.composedeskkit.internal.utils.OS
import io.github.kdroidfilter.composedeskkit.internal.utils.currentArch
import io.github.kdroidfilter.composedeskkit.internal.utils.currentOS
import io.github.kdroidfilter.composedeskkit.internal.utils.ioFile
import io.github.kdroidfilter.composedeskkit.internal.utils.notNullProperty
import io.github.kdroidfilter.composedeskkit.internal.utils.nullableProperty
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * Gradle task that packages a pre-built app-image (from jpackage) using electron-builder.
 *
 * Pipeline:
 *   1. Resolve the platform-specific app directory from the jpackage app-image output.
 *   2. Update the executable type in the app image's .cfg launcher file.
 *   3. Generate an electron-builder YAML configuration from the DSL settings.
 *   4. Invoke electron-builder via npx with `--prepackaged`.
 *   5. Output the final installer/package to [destinationDir].
 */
abstract class AbstractElectronBuilderPackageTask
    @Inject
    constructor(
        @get:Input val targetFormat: TargetFormat,
    ) : AbstractComposeDesktopTask() {
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        val appImageRoot: DirectoryProperty = objects.directoryProperty()

        @get:OutputDirectory
        val destinationDir: DirectoryProperty = objects.directoryProperty()

        @get:Input
        val packageName: Property<String> = objects.notNullProperty()

        @get:Input
        @get:Optional
        val packageVersion: Property<String?> = objects.nullableProperty()

        @get:Input
        @get:Optional
        val customNodePath: Property<String?> = objects.nullableProperty()

        /**
         * The distributions DSL object providing all platform-specific settings.
         * Marked @Internal because the individual settings are tracked via other @Input properties
         * on the DSL objects themselves, and this reference is used for config generation only.
         */
        @get:Internal
        var distributions: JvmApplicationDistributions? = null

        @TaskAction
        fun run() {
            val dist =
                this.distributions
                    ?: throw GradleException("distributions must be set on AbstractElectronBuilderPackageTask")

            if (!targetFormat.isCompatibleWithCurrentOS) {
                logger.lifecycle(
                    "Skipping ${targetFormat.name} packaging: not compatible with current OS ($currentOS)",
                )
                return
            }

            val appDir = resolveAppImageDir()
            logger.info("Resolved app image directory: ${appDir.absolutePath}")

            ensureResourcesDirForElectronBuilder(appDir)
            updateExecutableTypeInAppImage(appDir, targetFormat, logger)

            val npx = detectNpx()
            validateNodeVersion()

            val outputDir = destinationDir.ioFile.apply { mkdirs() }
            val configFile = generateConfig(dist, appDir, outputDir)
            ensureProjectPackageMetadata(outputDir, dist)

            val toolManager = ElectronBuilderToolManager(execOperations, logger)
            toolManager.invoke(
                ElectronBuilderInvocation(
                    configFile = configFile,
                    prepackagedDir = appDir,
                    outputDir = outputDir,
                    targets = buildElectronBuilderTargets(),
                    npx = npx,
                    environment =
                        resolveElectronBuilderEnvironment(
                            targetFormat = targetFormat,
                            currentOs = currentOS,
                            currentArchitecture = currentArch,
                            logger = logger,
                        ),
                ),
            )

            configFile.delete()
            logger.lifecycle("electron-builder package written to ${outputDir.canonicalPath}")
        }

        private fun detectNpx(): File =
            NodeJsDetector.detectNpx(
                customNodePath = customNodePath.orNull,
                logger = logger,
            ) ?: throw GradleException(
                "npx not found. Node.js 18+ is required for electron-builder packaging. " +
                    "Install Node.js or set the 'compose.electronBuilder.nodePath' Gradle property.",
            )

        private fun validateNodeVersion() {
            val node =
                NodeJsDetector.detectNode(
                    customNodePath = customNodePath.orNull,
                    logger = logger,
                ) ?: return
            val version = NodeJsDetector.getNodeVersion(node) ?: return
            if (!NodeJsDetector.isNodeVersionSupported(version)) {
                throw GradleException(
                    "Node.js $version is not supported. Version 18+ is required for electron-builder.",
                )
            }
            logger.info("Using Node.js: ${node.absolutePath} ($version)")
        }

        private fun generateConfig(
            distributions: JvmApplicationDistributions,
            appDir: File,
            outputDir: File,
        ): File {
            val configGenerator = ElectronBuilderConfigGenerator()
            val configContent =
                configGenerator.generateConfig(
                    distributions = distributions,
                    targetFormat = targetFormat,
                    appImageDir = appDir,
                )
            val configFile = File(outputDir, "electron-builder.yml")
            configFile.writeText(configContent)
            logger.info("Generated electron-builder config at: ${configFile.absolutePath}")
            return configFile
        }

        private fun ensureResourcesDirForElectronBuilder(appDir: File) {
            if (currentOS == OS.MacOS) return
            val resourcesDir = appDir.resolve("resources")
            if (!resourcesDir.exists()) {
                resourcesDir.mkdirs()
            }
        }

        private fun ensureProjectPackageMetadata(
            outputDir: File,
            distributions: JvmApplicationDistributions,
        ) {
            val packageJson = File(outputDir, "package.json")
            if (packageJson.exists()) return

            val normalizedName = packageName.get().toNpmPackageName()
            val normalizedVersion = packageVersion.orNull?.takeIf { it.isNotBlank() } ?: "1.0.0"
            val normalizedDescription =
                distributions.description?.takeIf { it.isNotBlank() }
                    ?: "Packaged desktop application"
            val normalizedAuthor =
                distributions.vendor?.takeIf { it.isNotBlank() }
                    ?: "Unknown"

            packageJson.writeText(
                """
                {
                  "name": "${normalizedName.escapeForJson()}",
                  "version": "${normalizedVersion.escapeForJson()}",
                  "description": "${normalizedDescription.escapeForJson()}",
                  "author": "${normalizedAuthor.escapeForJson()}",
                  "private": true
                }
                """.trimIndent(),
            )
            logger.info("Generated package metadata for electron-builder: ${packageJson.absolutePath}")
        }

        private fun String.toNpmPackageName(): String =
            lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9._-]"), "-")
                .trim('-')
                .ifBlank { "app" }

        private fun String.escapeForJson(): String =
            replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

        /**
         * Resolves the actual app directory inside the jpackage app-image output.
         *
         * jpackage produces: `<destinationDir>/<packageName>` on Linux/Windows
         *                  or `<destinationDir>/<packageName>.app` on macOS.
         */
        private fun resolveAppImageDir(): File {
            val root = appImageRoot.ioFile
            if (!root.isDirectory) {
                throw GradleException("App image directory not found: ${root.absolutePath}")
            }

            val name = packageName.get()

            // Try platform-specific name, then plain name, then single-child fallback
            val resolved =
                when {
                    currentOS == OS.MacOS && root.resolve("$name.app").isDirectory ->
                        root.resolve("$name.app")
                    root.resolve(name).isDirectory -> root.resolve(name)
                    else -> root.listFiles()?.singleOrNull { it.isDirectory }
                }

            return resolved ?: throw GradleException(
                "Unable to locate app image directory. " +
                    "Expected '$name' or '$name.app' inside: ${root.absolutePath}",
            )
        }

        /**
         * Builds the electron-builder CLI target arguments based on the current OS and target format.
         *
         * electron-builder uses platform flags: `--linux`, `--win`, `--mac`
         * followed by the target type (e.g., `deb`, `nsis`, `dmg`).
         */
        private fun buildElectronBuilderTargets(): List<String> {
            val platformFlag =
                when (currentOS) {
                    OS.Linux -> "--linux"
                    OS.Windows -> "--win"
                    OS.MacOS -> "--mac"
                }

            return listOf(platformFlag, targetFormat.electronBuilderTarget)
        }
    }

private fun resolveElectronBuilderEnvironment(
    targetFormat: TargetFormat,
    currentOs: OS,
    currentArchitecture: Arch,
    logger: Logger,
): Map<String, String> {
    val shouldAutoConfigureSignTool = currentOs == OS.Windows && targetFormat == TargetFormat.AppX
    val noExternalSignToolConfigured =
        System.getenv("SIGNTOOL_PATH").isNullOrBlank() &&
            System.getenv("WINDOWS_SIGNTOOL_PATH").isNullOrBlank()

    val signToolPath =
        if (shouldAutoConfigureSignTool && noExternalSignToolConfigured) {
            val architectureId =
                when (currentArchitecture) {
                    Arch.X64 -> "x64"
                    Arch.Arm64 -> "arm64"
                }
            WindowsKitsLocator.locateSignTool(architectureId)?.absolutePath
        } else {
            null
        }

    return if (signToolPath != null) {
        logger.info("Using Windows SDK SignTool for AppX signing: $signToolPath")
        mapOf(
            "SIGNTOOL_PATH" to signToolPath,
            "WINDOWS_SIGNTOOL_PATH" to signToolPath,
        )
    } else {
        emptyMap()
    }
}
