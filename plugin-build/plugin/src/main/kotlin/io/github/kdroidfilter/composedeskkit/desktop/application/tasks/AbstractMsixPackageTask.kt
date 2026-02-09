/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.tasks

import io.github.kdroidfilter.composedeskkit.desktop.application.internal.WindowsKitsLocator
import io.github.kdroidfilter.composedeskkit.desktop.tasks.AbstractComposeDesktopTask
import io.github.kdroidfilter.composedeskkit.internal.utils.Arch
import io.github.kdroidfilter.composedeskkit.internal.utils.OS
import io.github.kdroidfilter.composedeskkit.internal.utils.currentArch
import io.github.kdroidfilter.composedeskkit.internal.utils.currentOS
import io.github.kdroidfilter.composedeskkit.internal.utils.ioFile
import io.github.kdroidfilter.composedeskkit.internal.utils.notNullProperty
import io.github.kdroidfilter.composedeskkit.internal.utils.nullableProperty
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import javax.imageio.ImageIO

@Suppress("TooManyFunctions")
abstract class AbstractMsixPackageTask : AbstractComposeDesktopTask() {
    companion object {
        private const val ENV_SIGN_PFX_BASE64 = "MSIX_SIGN_PFX_BASE64"
        private const val ENV_SIGN_PASSWORD = "MSIX_SIGN_PFX_PASSWORD"
        private const val STORE_LOGO_SIZE = 256
        private const val SQUARE_44_LOGO_SIZE = 44
        private const val SQUARE_150_LOGO_SIZE = 150
        private const val MIN_MSIX_VERSION_SEGMENTS = 3
        private const val MAX_MSIX_VERSION_SEGMENTS = 4
        private const val MAX_MSIX_VERSION_SEGMENT_VALUE = 65535
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val appImageRoot: DirectoryProperty = objects.directoryProperty()

    @get:OutputDirectory
    val destinationDir: DirectoryProperty = objects.directoryProperty()

    @get:Input
    val packageName: Property<String> = objects.notNullProperty()

    @get:Input
    val packageVersion: Property<String> = objects.notNullProperty()

    @get:Input
    @get:Optional
    val packageDescription: Property<String?> = objects.nullableProperty()

    @get:Input
    @get:Optional
    val packageVendor: Property<String?> = objects.nullableProperty()

    @get:Input
    val identityName: Property<String> = objects.notNullProperty()

    @get:Input
    val publisher: Property<String> = objects.notNullProperty()

    @get:Input
    val publisherDisplayName: Property<String> = objects.notNullProperty()

    @get:Input
    val displayName: Property<String> = objects.notNullProperty()

    @get:Input
    val visualDescription: Property<String> = objects.notNullProperty()

    @get:Input
    val backgroundColor: Property<String> = objects.notNullProperty("transparent")

    @get:Input
    val appExecutable: Property<String> = objects.notNullProperty()

    @get:Input
    val appId: Property<String> = objects.notNullProperty("App")

    @get:Input
    val processorArchitecture: Property<String> = objects.notNullProperty()

    @get:Input
    val targetDeviceFamilyName: Property<String> = objects.notNullProperty("Windows.Desktop")

    @get:Input
    val targetDeviceFamilyMinVersion: Property<String> = objects.notNullProperty("10.0.17763.0")

    @get:Input
    val targetDeviceFamilyMaxVersionTested: Property<String> = objects.notNullProperty("10.0.22621.2861")

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val iconFile: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val manifestTemplateFile: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val signingPfxFile: RegularFileProperty = objects.fileProperty()

    @get:Input
    @get:Optional
    val signingPassword: Property<String?> = objects.nullableProperty()

    @TaskAction
    fun run() {
        if (currentOS != OS.Windows) {
            logger.lifecycle("Skipping MSIX packaging on non-Windows host")
            return
        }

        val appDir = resolveAppImageDir()
        val resourcesDir = appDir.resolve("resources").apply { mkdirs() }
        renderMsixIcons(resourcesDir)
        writeManifest(appDir)

        val outputDir = destinationDir.ioFile.apply { mkdirs() }
        val outputFile = outputDir.resolve("${packageName.get()}-${packageVersion.get()}_${archSuffix()}.msix")
        if (outputFile.exists() && !outputFile.delete()) {
            throw GradleException("Unable to delete existing MSIX file: ${outputFile.absolutePath}")
        }

        val makeAppx = resolveMakeAppxTool()
        runExternalTool(
            tool = makeAppx,
            args =
                listOf(
                    "pack",
                    "/d",
                    appDir.absolutePath,
                    "/p",
                    outputFile.absolutePath,
                    "/o",
                ),
            workingDir = makeAppx.parentFile,
        )

        signIfConfigured(outputFile)

        logger.lifecycle("The distribution is written to ${outputFile.canonicalPath}")
    }

    @Suppress("ReturnCount")
    private fun resolveAppImageDir(): File {
        val root = appImageRoot.ioFile
        if (!root.isDirectory) {
            throw GradleException("App image directory not found: ${root.absolutePath}")
        }

        val executable = appExecutable.get()
        if (root.resolve(executable).isFile) return root

        val expectedDir = root.resolve(packageName.get())
        if (expectedDir.isDirectory && expectedDir.resolve(executable).isFile) {
            return expectedDir
        }

        val candidates =
            root
                .listFiles()
                ?.filter { it.isDirectory && it.resolve(executable).isFile }
                .orEmpty()
        if (candidates.size == 1) return candidates.single()

        throw GradleException(
            "Unable to locate app image root containing '$executable'. " +
                "Checked: ${root.absolutePath}",
        )
    }

    private fun writeManifest(appDir: File) {
        val template =
            if (manifestTemplateFile.isPresent) {
                manifestTemplateFile.ioFile.readText()
            } else {
                javaClass.classLoader
                    .getResourceAsStream("default-appx-manifest.xml")
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Could not load default MSIX manifest template")
            }

        val rendered =
            template
                .replace("{{identityName}}", xmlEscape(identityName.get()))
                .replace("{{publisher}}", xmlEscape(publisher.get()))
                .replace("{{version}}", normalizeMsixVersion(packageVersion.get()))
                .replace("{{processorArchitecture}}", xmlEscape(processorArchitecture.get()))
                .replace("{{displayName}}", xmlEscape(displayName.get()))
                .replace("{{publisherDisplayName}}", xmlEscape(publisherDisplayName.get()))
                .replace("{{description}}", xmlEscape(visualDescription.get()))
                .replace("{{backgroundColor}}", xmlEscape(backgroundColor.get()))
                .replace("{{appExecutable}}", xmlEscape(appExecutable.get()))
                .replace("{{appId}}", xmlEscape(appId.get()))
                .replace("{{targetDeviceFamilyName}}", xmlEscape(targetDeviceFamilyName.get()))
                .replace("{{targetDeviceFamilyMinVersion}}", xmlEscape(targetDeviceFamilyMinVersion.get()))
                .replace("{{targetDeviceFamilyMaxVersionTested}}", xmlEscape(targetDeviceFamilyMaxVersionTested.get()))

        appDir.resolve("AppxManifest.xml").writeText(rendered)
    }

    @Suppress("ThrowsCount")
    private fun renderMsixIcons(resourcesDir: File) {
        val source =
            iconFile.orNull?.asFile
                ?: throw GradleException(
                    "No icon file configured for MSIX packaging. " +
                        "Set nativeDistributions.windows.msix.iconFile",
                )
        if (!source.isFile) {
            throw GradleException("MSIX icon file not found: ${source.absolutePath}")
        }

        val outputs =
            listOf(
                "StoreLogo.png" to STORE_LOGO_SIZE,
                "Square44x44Logo.png" to SQUARE_44_LOGO_SIZE,
                "Square150x150Logo.png" to SQUARE_150_LOGO_SIZE,
            )

        if (source.extension.equals("svg", ignoreCase = true)) {
            outputs.forEach { (name, size) ->
                renderSvgToPng(source, resourcesDir.resolve(name), size)
            }
            return
        }

        val sourceImage =
            ImageIO.read(source)
                ?: throw GradleException(
                    "Unsupported MSIX icon format: ${source.name}. " +
                        "Use PNG or SVG.",
                )
        outputs.forEach { (name, size) ->
            writeScaledPng(sourceImage, resourcesDir.resolve(name), size)
        }
    }

    private fun renderSvgToPng(
        svgFile: File,
        outputFile: File,
        size: Int,
    ) {
        val transcoder = PNGTranscoder()
        transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, size.toFloat())
        transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, size.toFloat())
        svgFile.inputStream().use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                transcoder.transcode(
                    TranscoderInput(inputStream),
                    TranscoderOutput(outputStream),
                )
            }
        }
    }

    private fun writeScaledPng(
        source: BufferedImage,
        outputFile: File,
        size: Int,
    ) {
        val target = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = target.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.drawImage(source, 0, 0, size, size, null)
        g2d.dispose()
        ImageIO.write(target, "png", outputFile)
    }

    private fun resolveMakeAppxTool(): File {
        val architecture = processorArchitecture.get()
        return WindowsKitsLocator.locateMakeAppx(architecture)
            ?: throw GradleException(
                "makeappx.exe not found for architecture '$architecture'. " +
                    "Install the Windows SDK Desktop C++ tools.",
            )
    }

    private fun resolveSignTool(): File {
        val architecture = processorArchitecture.get()
        return WindowsKitsLocator.locateSignTool(architecture)
            ?: throw GradleException(
                "signtool.exe not found for architecture '$architecture'. " +
                    "Install the Windows SDK Desktop C++ tools.",
            )
    }

    private fun signIfConfigured(outputFile: File) {
        val signingConfig = resolveSigningConfig() ?: return
        try {
            val signTool = resolveSignTool()
            runExternalTool(
                tool = signTool,
                args =
                    listOf(
                        "sign",
                        "/fd",
                        "SHA256",
                        "/f",
                        signingConfig.pfxFile.absolutePath,
                        "/p",
                        signingConfig.password,
                        outputFile.absolutePath,
                    ),
                workingDir = signTool.parentFile,
            )
        } finally {
            signingConfig.cleanup?.invoke()
        }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    private fun resolveSigningConfig(): SigningConfig? {
        val pfxFromDsl = signingPfxFile.orNull?.asFile
        val passwordFromDsl = signingPassword.orNull?.trim().takeUnless { it.isNullOrEmpty() }

        val envPfxBase64 = System.getenv(ENV_SIGN_PFX_BASE64)?.trim().takeUnless { it.isNullOrEmpty() }
        val envPassword = System.getenv(ENV_SIGN_PASSWORD)?.trim().takeUnless { it.isNullOrEmpty() }

        if (pfxFromDsl != null) {
            if (!pfxFromDsl.exists()) {
                if (envPfxBase64 == null) {
                    throw GradleException("MSIX signing PFX file not found: ${pfxFromDsl.absolutePath}")
                }
            } else {
                val password =
                    passwordFromDsl ?: envPassword
                        ?: throw GradleException(
                            "MSIX signing password is required when signingPfxFile is configured.",
                        )
                return SigningConfig(pfxFromDsl, password)
            }
        }

        if (envPfxBase64 == null) return null

        val password =
            passwordFromDsl ?: envPassword
                ?: throw GradleException(
                    "MSIX signing password is required when $ENV_SIGN_PFX_BASE64 is set.",
                )

        val tempPfx = temporaryDir.resolve("msix-signing.pfx")
        val bytes =
            try {
                Base64.getDecoder().decode(envPfxBase64)
            } catch (e: IllegalArgumentException) {
                throw GradleException("$ENV_SIGN_PFX_BASE64 does not contain valid base64 data", e)
            }
        tempPfx.parentFile.mkdirs()
        tempPfx.writeBytes(bytes)

        return SigningConfig(
            pfxFile = tempPfx,
            password = password,
            cleanup = { tempPfx.delete() },
        )
    }

    private fun normalizeMsixVersion(version: String): String {
        val parts = version.split(".")
        val asInts = parts.map { it.toIntOrNull() }
        if (
            parts.size !in MIN_MSIX_VERSION_SEGMENTS..MAX_MSIX_VERSION_SEGMENTS ||
            asInts.any { it == null || it !in 0..MAX_MSIX_VERSION_SEGMENT_VALUE }
        ) {
            throw GradleException(
                "Invalid MSIX version '$version'. " +
                    "Expected A.B.C or A.B.C.D with each segment between 0 and $MAX_MSIX_VERSION_SEGMENT_VALUE.",
            )
        }
        return if (parts.size == MIN_MSIX_VERSION_SEGMENTS) "$version.0" else version
    }

    private fun archSuffix(): String =
        when (currentArch) {
            Arch.X64 -> "x64"
            Arch.Arm64 -> "arm64"
        }

    private fun xmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private data class SigningConfig(
        val pfxFile: File,
        val password: String,
        val cleanup: (() -> Unit)? = null,
    )
}
