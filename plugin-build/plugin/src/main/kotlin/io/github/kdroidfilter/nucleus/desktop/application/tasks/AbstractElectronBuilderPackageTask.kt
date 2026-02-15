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
import java.io.ByteArrayOutputStream
import java.io.File
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.math.min
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
        companion object {
            private const val APPX_STORE_LOGO_SIZE = 50
            private const val APPX_SQUARE44_LOGO_SIZE = 44
            private const val APPX_SQUARE150_LOGO_SIZE = 150
            private const val APPX_WIDE_LOGO_WIDTH = 310
            private const val APPX_WIDE_LOGO_HEIGHT = 150
        }

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
        val windowsIconFile: RegularFileProperty = objects.fileProperty()

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
            if (shouldSkipForMissingTool()) return

            val appDir = resolveAppImageDir()
            logger.info("Resolved app image directory: ${appDir.absolutePath}")

            ensureResourcesDirForElectronBuilder(appDir)
            ensureLinuxExecutableAlias(appDir)
            updateExecutableTypeInAppImage(appDir, targetFormat, logger)
            ensureMacAdHocSigning(appDir, targetFormat)

            val npx = detectNpx()
            validateNodeVersion()

            val outputDir = destinationDir.ioFile.apply { mkdirs() }
            val linuxIconOverride = prepareLinuxIconSet(outputDir)
            val windowsIconOverride = resolveWindowsIcon()
            val linuxAfterInstallTemplate = prepareLinuxAfterInstallTemplate(outputDir)
            if (targetFormat == TargetFormat.AppX) {
                val hasExplicitWindowsIcon = dist.windows.iconFile.orNull?.asFile != null
                stageAppXAssets(
                    outputDir = outputDir,
                    windowsIconOverride = windowsIconOverride,
                    hasExplicitWindowsIcon = hasExplicitWindowsIcon,
                )
            }
            val configFile =
                generateConfig(
                    distributions = dist,
                    appDir = appDir,
                    outputDir = outputDir,
                    linuxIconOverride = linuxIconOverride,
                    windowsIconOverride = windowsIconOverride,
                    linuxAfterInstallTemplate = linuxAfterInstallTemplate,
                )
            ensureProjectPackageMetadata(outputDir, dist)

            val toolManager = ElectronBuilderToolManager(execOperations, logger)
            val extraConfigArgs =
                buildList {
                    if (targetFormat == TargetFormat.Snap && dist.publish.github.enabled) {
                        add("--config.snap.publish=github")
                    }
                }
            toolManager.invoke(
                ElectronBuilderInvocation(
                    configFile = configFile,
                    prepackagedDir = appDir,
                    outputDir = outputDir,
                    targets = buildElectronBuilderTargets(),
                    extraConfigArgs = extraConfigArgs,
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
            windowsIconOverride: File?,
            linuxAfterInstallTemplate: File?,
        ): File {
            val configGenerator = ElectronBuilderConfigGenerator()
            val configContent =
                configGenerator.generateConfig(
                    distributions = distributions,
                    targetFormat = targetFormat,
                    appImageDir = appDir,
                    startupWMClass = startupWMClass.orNull,
                    linuxIconOverride = linuxIconOverride,
                    windowsIconOverride = windowsIconOverride,
                    linuxAfterInstallTemplate = linuxAfterInstallTemplate,
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

        private fun ensureMacAdHocSigning(
            appDir: File,
            targetFormat: TargetFormat,
        ) {
            if (currentOS != OS.MacOS) return
            if (!appDir.isDirectory) return

            // Skip ad-hoc signing for PKG - it works better without it
            // PKG installers handle signing differently than DMG
            if (targetFormat == TargetFormat.Pkg) {
                logger.info("Skipping ad-hoc signing for PKG format")
                return
            }

            logger.info("Applying ad-hoc code signature to macOS app before electron-builder packaging")

            // Sign with "-" (ad-hoc) to stabilize the app structure
            // This prevents issues when electron-builder repackages the app
            execOperations.exec { spec ->
                spec.executable = "codesign"
                spec.args =
                    listOf(
                        "--force", // Overwrite existing signature
                        "--deep", // Sign nested code
                        "--sign",
                        "-", // Ad-hoc signing (no certificate)
                        appDir.absolutePath,
                    )
                spec.isIgnoreExitValue = false
            }

            logger.info("Ad-hoc signature applied successfully")
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

        private fun resolveWindowsIcon(): File? {
            if (currentOS != OS.Windows) return null

            val iconFile = windowsIconFile.orNull?.asFile ?: return null
            if (!iconFile.isFile) {
                logger.warn("Windows icon file not found: ${iconFile.absolutePath}")
                return null
            }
            return iconFile
        }

        private data class AppXAsset(
            val targetFileName: String,
            val width: Int,
            val height: Int,
            val source: File?,
        )

        private fun stageAppXAssets(
            outputDir: File,
            windowsIconOverride: File?,
            hasExplicitWindowsIcon: Boolean,
        ) {
            val stagedAssetsDir = outputDir.resolve("build").resolve("appx")
            stagedAssetsDir.deleteRecursively()

            val assets = appXAssets()
            validateAppXAssetSources(assets)
            val fallbackImage = resolveAppXFallbackImage(windowsIconOverride, hasExplicitWindowsIcon)

            if (assets.none { it.source != null } && fallbackImage == null) return

            stagedAssetsDir.mkdirs()
            copyOrGenerateAppXAssets(assets, stagedAssetsDir, fallbackImage)

            if (assets.any { it.source == null } && fallbackImage == null) {
                logger.warn(
                    "Some AppX assets are missing and no readable fallback icon was found. " +
                        "Provide AppX logo files explicitly to avoid incomplete assets.",
                )
            }
        }

        private fun appXAssets(): List<AppXAsset> =
            listOf(
                AppXAsset("StoreLogo.png", APPX_STORE_LOGO_SIZE, APPX_STORE_LOGO_SIZE, appxStoreLogo.orNull?.asFile),
                AppXAsset(
                    "Square44x44Logo.png",
                    APPX_SQUARE44_LOGO_SIZE,
                    APPX_SQUARE44_LOGO_SIZE,
                    appxSquare44x44Logo.orNull?.asFile,
                ),
                AppXAsset(
                    "Square150x150Logo.png",
                    APPX_SQUARE150_LOGO_SIZE,
                    APPX_SQUARE150_LOGO_SIZE,
                    appxSquare150x150Logo.orNull?.asFile,
                ),
                AppXAsset(
                    "Wide310x150Logo.png",
                    APPX_WIDE_LOGO_WIDTH,
                    APPX_WIDE_LOGO_HEIGHT,
                    appxWide310x150Logo.orNull?.asFile,
                ),
            )

        private fun validateAppXAssetSources(assets: List<AppXAsset>) {
            for (asset in assets) {
                val source = asset.source ?: continue
                if (!source.isFile) {
                    throw GradleException("AppX asset file not found: ${source.absolutePath}")
                }
            }
        }

        private fun resolveAppXFallbackImage(
            windowsIconOverride: File?,
            hasExplicitWindowsIcon: Boolean,
        ): BufferedImage? =
            readImage(windowsIconOverride)
                ?: if (!hasExplicitWindowsIcon) readImage(linuxIconFile.orNull?.asFile) else null

        private fun copyOrGenerateAppXAssets(
            assets: List<AppXAsset>,
            stagedAssetsDir: File,
            fallbackImage: BufferedImage?,
        ) {
            for (asset in assets) {
                val target = stagedAssetsDir.resolve(asset.targetFileName)
                val source = asset.source
                if (source != null) {
                    source.copyTo(target, overwrite = true)
                } else if (fallbackImage != null) {
                    val generated = resizeIconToCanvas(fallbackImage, asset.width, asset.height)
                    ImageIO.write(generated, "png", target)
                }
            }
        }

        private fun readImage(file: File?): BufferedImage? {
            if (file == null || !file.isFile) return null
            return ImageIO.read(file)
        }

        private fun shouldSkipForMissingTool(): Boolean {
            if (currentOS != OS.Linux) return false

            return when (targetFormat) {
                TargetFormat.Snap -> {
                    if (!isCommandAvailable("snapcraft")) {
                        logger.lifecycle("Skipping Snap packaging: 'snapcraft' is not available on this runner.")
                        true
                    } else if (currentArch == Arch.Arm64) {
                        logger.lifecycle(
                            "Skipping Snap packaging on arm64: electron-builder uses build-snaps (gnome-3-28-1804) unavailable for arm64.",
                        )
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        private fun isCommandAvailable(command: String): Boolean {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            return try {
                val result =
                    execOperations.exec { spec ->
                        spec.executable = "sh"
                        spec.args = listOf("-lc", "command -v $command >/dev/null 2>&1")
                        spec.isIgnoreExitValue = true
                        spec.standardOutput = stdout
                        spec.errorOutput = stderr
                    }
                result.exitValue == 0
            } catch (_: Exception) {
                false
            }
        }

        private fun prepareLinuxAfterInstallTemplate(outputDir: File): File? {
            if (currentOS != OS.Linux) return null
            if (targetFormat != TargetFormat.Deb && targetFormat != TargetFormat.Rpm) return null

            val templateFile = outputDir.resolve("after-install-nucleus.tpl")
            val script =
                $$"""
                #!/bin/bash

                if type update-alternatives >/dev/null 2>&1; then
                    # Remove previous link if it doesn't use update-alternatives
                    if [ -L '/usr/bin/${executable}' -a -e '/usr/bin/${executable}' -a "`readlink '/usr/bin/${executable}'`" != '/etc/alternatives/${executable}' ]; then
                        rm -f '/usr/bin/${executable}'
                    fi
                    update-alternatives --install '/usr/bin/${executable}' '${executable}' '/opt/${sanitizedProductName}/${executable}' 100 || ln -sf '/opt/${sanitizedProductName}/${executable}' '/usr/bin/${executable}'
                else
                    ln -sf '/opt/${sanitizedProductName}/${executable}' '/usr/bin/${executable}'
                fi

                SANDBOX_PATH='/opt/${sanitizedProductName}/chrome-sandbox'
                if [ -e "$SANDBOX_PATH" ]; then
                    # Check if user namespaces are supported by the kernel and working with a quick test:
                    if ! { [[ -L /proc/self/ns/user ]] && unshare --user true; }; then
                        # Use SUID chrome-sandbox only on systems without user namespaces:
                        chmod 4755 "$SANDBOX_PATH" || true
                    else
                        chmod 0755 "$SANDBOX_PATH" || true
                    fi
                fi

                if hash update-mime-database 2>/dev/null; then
                    update-mime-database /usr/share/mime || true
                fi

                if hash update-desktop-database 2>/dev/null; then
                    update-desktop-database /usr/share/applications || true
                fi

                # Install apparmor profile. (Ubuntu 24+)
                # First check if the version of AppArmor running on the device supports our profile.
                # This is in order to keep backwards compatibility with Ubuntu 22.04 which does not support abi/4.0.
                # In that case, we just skip installing the profile since the app runs fine without it on 22.04.
                #
                # Those apparmor_parser flags are akin to performing a dry run of loading a profile.
                # https://wiki.debian.org/AppArmor/HowToUse#Dumping_profiles
                #
                # Unfortunately, at the moment AppArmor doesn't have a good story for backwards compatibility.
                # https://askubuntu.com/questions/1517272/writing-a-backwards-compatible-apparmor-profile
                if apparmor_status --enabled > /dev/null 2>&1; then
                  APPARMOR_PROFILE_SOURCE='/opt/${sanitizedProductName}/resources/apparmor-profile'
                  APPARMOR_PROFILE_TARGET='/etc/apparmor.d/${executable}'
                  if apparmor_parser --skip-kernel-load --debug "$APPARMOR_PROFILE_SOURCE" > /dev/null 2>&1; then
                    cp -f "$APPARMOR_PROFILE_SOURCE" "$APPARMOR_PROFILE_TARGET"

                    # Updating the current AppArmor profile is not possible and probably not meaningful in a chroot'ed environment.
                    # Use cases are for example environments where images for clients are maintained.
                    # There, AppArmor might correctly be installed, but live updating makes no sense.
                    if ! { [ -x '/usr/bin/ischroot' ] && /usr/bin/ischroot; } && hash apparmor_parser 2>/dev/null; then
                      # Extra flags taken from dh_apparmor:
                      # > By using '-W -T' we ensure that any abstraction updates are also pulled in.
                      # https://wiki.debian.org/AppArmor/Contribute/FirstTimeProfileImport
                      apparmor_parser --replace --write-cache --skip-read-cache "$APPARMOR_PROFILE_TARGET"
                    fi
                  else
                    echo "Skipping the installation of the AppArmor profile as this version of AppArmor does not seem to support the bundled profile"
                  fi
                fi
                """.trimIndent() + "\n"

            templateFile.writeText(script)
            logger.info("Generated Linux after-install template at: ${templateFile.absolutePath}")
            return templateFile
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

        private fun resizeIconToCanvas(
            source: BufferedImage,
            width: Int,
            height: Int,
        ): BufferedImage {
            val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = resized.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.composite = AlphaComposite.Src
            graphics.fillRect(0, 0, width, height)

            val scale = min(width.toDouble() / source.width, height.toDouble() / source.height)
            val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
            val x = (width - targetWidth) / 2
            val y = (height - targetHeight) / 2
            graphics.drawImage(source, x, y, targetWidth, targetHeight, null)
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
            val repositoryUrl =
                distributions.publish.github
                    .takeIf { it.enabled }
                    ?.let { github ->
                        val owner = github.owner?.takeIf { value -> value.isNotBlank() }
                        val repo = github.repo?.takeIf { value -> value.isNotBlank() }
                        if (owner != null && repo != null) {
                            "https://github.com/$owner/$repo.git"
                        } else {
                            null
                        }
                    }
            val repositoryField =
                repositoryUrl?.let { value ->
                    ",\n  \"repository\": \"${value.escapeForJson()}\""
                } ?: ""

            packageJson.writeText(
                """
                {
                  "name": "${normalizedName.escapeForJson()}",
                  "version": "${normalizedVersion.escapeForJson()}",
                  "description": "${normalizedDescription.escapeForJson()}",
                  "author": "${normalizedAuthor.escapeForJson()}",
                  "private": true$repositoryField
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
    val env = mutableMapOf<String, String>()

    // macOS: disable automatic certificate discovery when no signing identity is configured
    if (currentOs == OS.MacOS) {
        env["CSC_IDENTITY_AUTO_DISCOVERY"] = "false"
    }

    // Windows: auto-configure SignTool for AppX signing
    val shouldAutoConfigureSignTool = currentOs == OS.Windows && targetFormat == TargetFormat.AppX
    val noExternalSignToolConfigured =
        System.getenv("SIGNTOOL_PATH").isNullOrBlank() &&
            System.getenv("WINDOWS_SIGNTOOL_PATH").isNullOrBlank()

    if (shouldAutoConfigureSignTool && noExternalSignToolConfigured) {
        val architectureId =
            when (currentArchitecture) {
                Arch.X64 -> "x64"
                Arch.Arm64 -> "arm64"
            }
        val signToolPath = WindowsKitsLocator.locateSignTool(architectureId)?.absolutePath
        if (signToolPath != null) {
            logger.info("Using Windows SDK SignTool for AppX signing: $signToolPath")
            env["SIGNTOOL_PATH"] = signToolPath
            env["WINDOWS_SIGNTOOL_PATH"] = signToolPath
        }
    }

    // Linux Snap: use destructive mode so snapcraft doesn't require LXD/multipass
    if (currentOs == OS.Linux && targetFormat == TargetFormat.Snap) {
        env["SNAPCRAFT_BUILD_ENVIRONMENT"] = "host"
    }

    return env
}
