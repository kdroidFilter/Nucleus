/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.tasks

import io.github.kdroidfilter.nucleus.desktop.application.dsl.JvmApplicationDistributions
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat
import io.github.kdroidfilter.nucleus.desktop.application.internal.WindowsKitsLocator
import io.github.kdroidfilter.nucleus.desktop.application.internal.electronbuilder.ElectronBuilderConfigGenerator
import io.github.kdroidfilter.nucleus.desktop.application.internal.electronbuilder.ElectronBuilderInvocation
import io.github.kdroidfilter.nucleus.desktop.application.internal.electronbuilder.ElectronBuilderToolManager
import io.github.kdroidfilter.nucleus.desktop.application.internal.electronbuilder.NodeJsDetector
import io.github.kdroidfilter.nucleus.desktop.application.internal.updateExecutableTypeInAppImage
import io.github.kdroidfilter.nucleus.desktop.tasks.AbstractNucleusTask
import io.github.kdroidfilter.nucleus.internal.utils.Arch
import io.github.kdroidfilter.nucleus.internal.utils.OS
import io.github.kdroidfilter.nucleus.internal.utils.currentArch
import io.github.kdroidfilter.nucleus.internal.utils.currentOS
import io.github.kdroidfilter.nucleus.internal.utils.ioFile
import io.github.kdroidfilter.nucleus.internal.utils.notNullProperty
import io.github.kdroidfilter.nucleus.internal.utils.nullableProperty
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Locale
import javax.inject.Inject
import javax.imageio.ImageIO

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
    ) : AbstractNucleusTask() {
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        val appImageRoot: DirectoryProperty = objects.directoryProperty()

        @get:OutputDirectory
        val destinationDir: DirectoryProperty = objects.directoryProperty()

        @get:Input
        val packageName: Property<String> = objects.notNullProperty()

        @get:Input
        @get:Optional
        val packageVersion: Property<String> = objects.nullableProperty()

        @get:Input
        @get:Optional
        val customNodePath: Property<String> = objects.nullableProperty()

        @get:Input
        @get:Optional
        val startupWMClass: Property<String> = objects.nullableProperty()

        @get:InputFile
        @get:Optional
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        val linuxIconFile: RegularFileProperty = objects.fileProperty()

        @get:InputFile
        @get:Optional
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        val appxStoreLogo: RegularFileProperty = objects.fileProperty()

        @get:InputFile
        @get:Optional
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        val appxSquare44x44Logo: RegularFileProperty = objects.fileProperty()

        @get:InputFile
        @get:Optional
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        val appxSquare150x150Logo: RegularFileProperty = objects.fileProperty()

        @get:InputFile
        @get:Optional
        @get:PathSensitive(PathSensitivity.ABSOLUTE)
        val appxWide310x150Logo: RegularFileProperty = objects.fileProperty()

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
            ensureLinuxExecutableAlias(appDir)
            updateExecutableTypeInAppImage(appDir, targetFormat, logger)

            val npx = detectNpx()
            validateNodeVersion()

            val outputDir = destinationDir.ioFile.apply { mkdirs() }
            val linuxIconOverride = prepareLinuxIconSet(outputDir)
            if (targetFormat == TargetFormat.AppX) {
                val stagedAssetsDir = outputDir.resolve("build").resolve("appx")
                stagedAssetsDir.deleteRecursively()

                val mappings =
                    listOf(
                        "StoreLogo.png" to appxStoreLogo.orNull?.asFile,
                        "Square44x44Logo.png" to appxSquare44x44Logo.orNull?.asFile,
                        "Square150x150Logo.png" to appxSquare150x150Logo.orNull?.asFile,
                        "Wide310x150Logo.png" to appxWide310x150Logo.orNull?.asFile,
                    )

                for ((targetFileName, source) in mappings) {
                    if (source == null) continue
                    if (!source.isFile) {
                        throw GradleException("AppX asset file not found: ${source.absolutePath}")
                    }
                    stagedAssetsDir.mkdirs()
                    source.copyTo(stagedAssetsDir.resolve(targetFileName), overwrite = true)
                }
            }
            val configFile = generateConfig(dist, appDir, outputDir, linuxIconOverride)
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
            linuxIconOverride: File?,
        ): File {
            val configGenerator = ElectronBuilderConfigGenerator()
            val configContent =
                configGenerator.generateConfig(
                    distributions = distributions,
                    targetFormat = targetFormat,
                    appImageDir = appDir,
                    startupWMClass = startupWMClass.orNull,
                    linuxIconOverride = linuxIconOverride,
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

        private fun prepareLinuxIconSet(outputDir: File): File? {
            if (currentOS != OS.Linux) return null

            val iconFile = linuxIconFile.orNull?.asFile ?: return null
            if (!iconFile.isFile) {
                logger.warn("Linux icon file not found: ${iconFile.absolutePath}")
                return null
            }

            val extension = iconFile.extension.lowercase(Locale.ROOT)
            if (extension != "png") {
                // Let electron-builder handle non-PNG icons as-is.
                return iconFile
            }

            val source = ImageIO.read(iconFile)
            if (source == null) {
                logger.warn("Unable to read Linux icon: ${iconFile.absolutePath}")
                return iconFile
            }

            val iconsDir = outputDir.resolve("linux-icons")
            if (iconsDir.exists()) iconsDir.deleteRecursively()
            iconsDir.mkdirs()

            val sizes = listOf(16, 32, 48, 64, 128, 256, 512)
            for (size in sizes) {
                val resized = resizeIcon(source, size, size)
                val target = iconsDir.resolve("${size}x${size}.png")
                ImageIO.write(resized, "png", target)
            }
            logger.info("Generated Linux icon set at: ${iconsDir.absolutePath}")
            return iconsDir
        }

        private fun resizeIcon(
            source: BufferedImage,
            width: Int,
            height: Int,
        ): BufferedImage {
            val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = resized.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, width, height, null)
            graphics.dispose()
            return resized
        }

        private fun ensureLinuxExecutableAlias(appDir: File) {
            if (currentOS != OS.Linux) return

            val launcherName = packageName.get()
            val launcher = appDir.resolve("bin").resolve(launcherName)
            if (!launcher.isFile) {
                logger.warn(
                    "Expected launcher not found at ${launcher.absolutePath}. " +
                        "Skipping Linux executable alias creation.",
                )
                return
            }

            val aliasName = launcherName.toNpmPackageName()
            val aliasFile = appDir.resolve(aliasName)
            if (aliasFile.exists()) return

            val script =
                $$"""
                #!/usr/bin/env sh
                SCRIPT="$0"
                while [ -L "$SCRIPT" ]; do
                  TARGET="$(readlink "$SCRIPT")"
                  case "$TARGET" in
                    /*) SCRIPT="$TARGET" ;;
                    *) SCRIPT="$(dirname "$SCRIPT")/$TARGET" ;;
                  esac
                done
                DIR="$(cd "$(dirname "$SCRIPT")" && pwd)"
                exec "$DIR/bin/$$launcherName" "$@"
                """.trimIndent() + "\n"

            aliasFile.writeText(script)
            // Ensure mode is effectively 0755 to keep launcher visible/runnable for non-root users.
            aliasFile.setReadable(true, false)
            aliasFile.setWritable(false, false)
            aliasFile.setWritable(true, true)
            aliasFile.setExecutable(true, false)
            logger.info("Created Linux launcher alias: ${aliasFile.absolutePath}")
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
