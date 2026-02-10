/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.tasks

import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.MacOSSigningSettings
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.MacSigner
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.MacSignerImpl
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.NoCertificateSigner
import io.github.kdroidfilter.composedeskkit.desktop.application.internal.validation.validate
import io.github.kdroidfilter.composedeskkit.desktop.tasks.AbstractComposeDesktopTask
import io.github.kdroidfilter.composedeskkit.internal.utils.MacUtils
import io.github.kdroidfilter.composedeskkit.internal.utils.currentOS
import io.github.kdroidfilter.composedeskkit.internal.utils.notNullProperty
import io.github.kdroidfilter.composedeskkit.internal.utils.nullableProperty
import io.github.kdroidfilter.composedeskkit.internal.utils.OS
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink

/**
 * Merges arm64 and x64 macOS distributables into a universal binary using `lipo`.
 *
 * Strategy:
 * 1. Copy the arm64 `.app` bundle as the base
 * 2. Walk the x64 `.app` and for each file that has an arm64 counterpart:
 *    - If it's a Mach-O binary → merge with `lipo -create`
 *    - Otherwise → keep the arm64 version
 * 3. Copy x64-only files (e.g. `libskiko-macos-x64.dylib`) into the output
 * 4. Clear extended attributes and re-sign
 */
@DisableCachingByDefault(because = "Merge depends on two distributable builds")
abstract class AbstractMergeUniversalBinaryTask : AbstractComposeDesktopTask() {

    @get:InputDirectory
    abstract val arm64AppDir: DirectoryProperty

    @get:InputDirectory
    abstract val x64AppDir: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val destinationDir: DirectoryProperty

    @get:InputFile
    @get:Optional
    val macEntitlementsFile: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    val macRuntimeEntitlementsFile: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    val macProvisioningProfile: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    val macRuntimeProvisioningProfile: RegularFileProperty = objects.fileProperty()

    @get:Input
    @get:Optional
    internal val nonValidatedMacBundleID: Property<String?> = objects.nullableProperty()

    @get:Input
    @get:Optional
    internal val macAppStore: Property<Boolean?> = objects.nullableProperty()

    @get:Optional
    @get:Nested
    internal var nonValidatedMacSigningSettings: MacOSSigningSettings? = null

    private val macSigner: MacSigner by lazy {
        val settings = nonValidatedMacSigningSettings
        if (settings?.sign?.get() == true) {
            val validated = settings.validate(nonValidatedMacBundleID, project, macAppStore)
            MacSignerImpl(validated, runExternalTool)
        } else {
            NoCertificateSigner(runExternalTool)
        }
    }

    @TaskAction
    fun run() {
        check(currentOS == OS.MacOS) { "Universal binary merge is only supported on macOS" }

        val appName = "${packageName.get()}.app"
        val arm64App = arm64AppDir.get().asFile.resolve(appName)
        val x64App = x64AppDir.get().asFile.resolve(appName)

        if (!arm64App.exists()) throw GradleException("arm64 app not found: $arm64App")
        if (!x64App.exists()) throw GradleException("x64 app not found: $x64App")

        val outputDir = destinationDir.get().asFile
        if (outputDir.exists()) outputDir.deleteRecursively()
        outputDir.mkdirs()

        val universalApp = outputDir.resolve(appName)
        logger.lifecycle("[universalBinary] Merging $appName (arm64 + x64) → $universalApp")

        // Step 1: Copy arm64 as base (preserving symlinks)
        copyPreservingSymlinks(arm64App, universalApp)

        // Step 2: Merge Mach-O binaries from x64 + copy x64-only files
        mergeX64Into(universalApp, x64App)

        // Step 3: Ensure .jpackage.xml exists with correct host version
        ensureJpackageXml(universalApp, x64App)

        // Step 4: Clear extended attributes
        clearExtendedAttributes(universalApp)

        // Step 5: Re-sign
        signApp(universalApp)

        logger.lifecycle("[universalBinary] Universal app created: $universalApp")
    }

    private fun mergeX64Into(universalApp: File, x64App: File) {
        var lipoCount = 0
        var copyCount = 0
        val x64Root = x64App.toPath()

        Files.walk(x64Root).use { stream ->
            stream.forEach { x64Path ->
                if (Files.isDirectory(x64Path, LinkOption.NOFOLLOW_LINKS)) return@forEach

                // Skip jpackage internal metadata (version-specific, causes conflicts)
                if (x64Path.fileName.toString() == ".jpackage.xml") return@forEach

                val relativePath = x64Root.relativize(x64Path)
                val universalPath = universalApp.toPath().resolve(relativePath)

                if (x64Path.isSymbolicLink()) {
                    // Preserve symlinks from x64 that don't exist yet in universal
                    if (!Files.exists(universalPath, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectories(universalPath.parent)
                        Files.copy(x64Path, universalPath, LinkOption.NOFOLLOW_LINKS)
                        copyCount++
                    }
                    return@forEach
                }

                val universalFile = universalPath.toFile()
                val x64File = x64Path.toFile()

                if (universalFile.exists()) {
                    // Both arm64 and x64 have this file
                    if (isMachOBinary(x64File)) {
                        // Merge with lipo
                        val tmpOutput = File.createTempFile("lipo-", "-${x64File.name}")
                        try {
                            runLipo(universalFile, x64File, tmpOutput)
                            tmpOutput.copyTo(universalFile, overwrite = true)
                            lipoCount++
                        } finally {
                            tmpOutput.delete()
                        }
                    }
                    // else: keep arm64 version (configs, jars, etc.)
                } else {
                    // x64-only file (e.g. libskiko-macos-x64.dylib)
                    universalFile.parentFile.mkdirs()
                    x64File.copyTo(universalFile)
                    copyCount++
                }
            }
        }

        logger.lifecycle("[universalBinary] Merged $lipoCount Mach-O binaries, copied $copyCount x64-only files")
    }

    private fun isMachOBinary(file: File): Boolean {
        if (file.length() < 4) return false
        if (file.toPath().isSymbolicLink()) return false

        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        val magic = ByteBuffer.wrap(header).int

        return when (magic) {
            0xFEEDFACF.toInt(), // Mach-O 64-bit (little-endian magic)
            0xCFFAEDFE.toInt(), // Mach-O 64-bit (big-endian magic)
            0xFEEDFACE.toInt(), // Mach-O 32-bit (little-endian magic)
            0xCEFAEDFE.toInt(), // Mach-O 32-bit (big-endian magic)
            0xCAFEBABE.toInt(), // Fat/universal binary
            -> true
            else -> false
        }
    }

    private fun runLipo(arm64File: File, x64File: File, output: File) {
        val lipo = File("/usr/bin/lipo")
        runExternalTool(
            tool = lipo,
            args = listOf(
                "-create",
                arm64File.absolutePath,
                x64File.absolutePath,
                "-output",
                output.absolutePath,
            ),
        )
    }

    private fun copyPreservingSymlinks(source: File, target: File) {
        val srcPath = source.toPath()
        val tgtPath = target.toPath()
        Files.walk(srcPath).use { stream ->
            stream.forEach { src ->
                val dest = tgtPath.resolve(srcPath.relativize(src))
                if (src.isSymbolicLink()) {
                    Files.createDirectories(dest.parent)
                    Files.copy(src, dest, LinkOption.NOFOLLOW_LINKS)
                } else if (Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(dest)
                } else {
                    Files.createDirectories(dest.parent)
                    Files.copy(
                        src, dest,
                        LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.COPY_ATTRIBUTES,
                    )
                }
            }
        }
    }

    private fun ensureJpackageXml(universalApp: File, x64App: File) {
        val appContentDir = File(universalApp, "Contents/app")
        val jpackageXml = File(appContentDir, ".jpackage.xml")
        if (jpackageXml.exists()) return // arm64 base already provided one

        // Use x64's .jpackage.xml as template
        val x64Xml = File(x64App, "Contents/app/.jpackage.xml")
        if (!x64Xml.exists()) {
            logger.warn("[universalBinary] No .jpackage.xml found in either build")
            return
        }

        // Get host jpackage version
        val jpackageBin = File(System.getProperty("java.home"), "bin/jpackage")
        val hostVersion = if (jpackageBin.exists()) {
            runCatching {
                val proc = ProcessBuilder(jpackageBin.absolutePath, "--version")
                    .redirectErrorStream(true).start()
                proc.inputStream.bufferedReader().readText().trim().also { proc.waitFor() }
            }.getOrDefault(System.getProperty("java.version") ?: "25")
        } else {
            System.getProperty("java.version") ?: "25"
        }

        // Copy and patch the version attribute
        val content = x64Xml.readText()
        val patched = content.replace(Regex("""version="[^"]*""""), """version="$hostVersion"""")
        appContentDir.mkdirs()
        jpackageXml.writeText(patched)
        logger.lifecycle("[universalBinary] Generated .jpackage.xml (host jpackage version=$hostVersion)")
    }

    private fun clearExtendedAttributes(appDir: File) {
        val xattr = File("/usr/bin/xattr")
        if (!xattr.exists()) return

        val process = ProcessBuilder(xattr.absolutePath, "-cr", appDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            logger.warn("[universalBinary] xattr -cr exited with $exitCode (non-fatal): $output")
        }
    }

    private fun signApp(appDir: File) {
        val appEntitlements = macEntitlementsFile.orNull?.asFile
        val runtimeEntitlements = macRuntimeEntitlementsFile.orNull?.asFile

        // Sign runtime provisioning profile if present
        macRuntimeProvisioningProfile.orNull?.asFile?.let { profile ->
            val runtimeDir = appDir.resolve("Contents/runtime")
            if (runtimeDir.exists()) {
                val target = runtimeDir.resolve("Contents/embedded.provisionprofile")
                profile.copyTo(target, overwrite = true)
            }
        }

        // Sign all binaries in the runtime
        val runtimeDir = appDir.resolve("Contents/runtime")
        if (runtimeDir.exists()) {
            runtimeDir.walkTopDown().forEach { file ->
                val path = file.toPath()
                if (path.isRegularFile(LinkOption.NOFOLLOW_LINKS) &&
                    (path.isExecutable() || file.name.endsWith(".dylib"))
                ) {
                    macSigner.sign(file, runtimeEntitlements)
                }
            }
            macSigner.sign(runtimeDir, runtimeEntitlements, forceEntitlements = true)
        }

        // Sign all dylibs and executables outside the runtime
        appDir.walkTopDown().forEach { file ->
            val path = file.toPath()
            if (path.isRegularFile(LinkOption.NOFOLLOW_LINKS) &&
                !file.absolutePath.contains("/runtime/") &&
                (file.name.endsWith(".dylib") || path.isExecutable())
            ) {
                macSigner.sign(file, appEntitlements)
            }
        }

        // Sign the app bundle
        macSigner.sign(appDir, appEntitlements, forceEntitlements = true)
    }
}
