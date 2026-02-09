/*
 * Copyright 2020-2024 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.internal

import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.DebCompression
import io.github.kdroidfilter.composedeskkit.desktop.application.dsl.RpmCompression
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File

internal object LinuxPackagePostProcessor {
    // Known libraries that changed names in Ubuntu 24.04+ (t64 transition)
    private val T64_REWRITES =
        mapOf(
            "libasound2" to "libasound2t64",
            "libfreetype6" to "libfreetype6t64",
            "libpng16-16" to "libpng16-16t64",
            "libtinfo5" to "libtinfo5t64",
            "libxml2" to "libxml2t64",
            "libfontconfig1" to "libfontconfig1t64",
        )

    fun postProcessDeb(
        debFile: File,
        startupWMClass: String,
        debDepends: List<String>,
        enableT64: Boolean,
        compression: DebCompression?,
        compressionLevel: Int?,
        execOperations: ExecOperations,
        logger: Logger,
    ) {
        logger.lifecycle("Post-processing .deb package: ${debFile.name}")

        val tmpDir = createTempDir("deb-postprocess")
        try {
            // Extract deb
            exec(execOperations, "dpkg-deb", listOf("-R", debFile.absolutePath, tmpDir.absolutePath))

            // Modify .desktop files
            val desktopFiles = tmpDir.walkTopDown().filter { it.isFile && it.extension == "desktop" }.toList()
            for (desktopFile in desktopFiles) {
                injectDesktopFileFields(desktopFile, startupWMClass)
                logger.lifecycle("  Injected StartupWMClass=$startupWMClass into ${desktopFile.name}")
            }

            // Modify DEBIAN/control for dependencies
            if (debDepends.isNotEmpty()) {
                val controlFile = tmpDir.resolve("DEBIAN/control")
                if (controlFile.exists()) {
                    modifyDebControl(controlFile, debDepends, enableT64, logger)
                }
            }

            // Repack deb
            val repackArgs = mutableListOf<String>()
            if (compression != null) {
                repackArgs.addAll(listOf("-Z", compression.id))
            }
            if (compressionLevel != null) {
                require(compression != null && compression != DebCompression.NONE) {
                    "debCompressionLevel requires debCompression to be set to GZIP, XZ, or ZSTD"
                }
                require(compressionLevel in 0..compression.maxLevel) {
                    "debCompressionLevel $compressionLevel is out of range for ${compression.id} (0..${compression.maxLevel})"
                }
                repackArgs.addAll(listOf("-z", compressionLevel.toString()))
            }
            repackArgs.addAll(listOf("-b", tmpDir.absolutePath, debFile.absolutePath))
            exec(execOperations, "dpkg-deb", repackArgs)
            logger.lifecycle(
                "  Repacked .deb: ${debFile.name}" +
                    (compression?.let { " (compression: ${it.id}${compressionLevel?.let { l -> ", level: $l" } ?: ""})" } ?: ""),
            )
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    fun postProcessRpm(
        rpmFile: File,
        startupWMClass: String,
        rpmRequires: List<String>,
        compression: RpmCompression?,
        compressionLevel: Int?,
        execOperations: ExecOperations,
        logger: Logger,
    ) {
        logger.lifecycle("Post-processing .rpm package: ${rpmFile.name}")

        val tmpDir = createTempDir("rpm-postprocess")
        try {
            // STAGING: extract and modify content here (rpmbuild will NOT touch this)
            val stagingDir = tmpDir.resolve("STAGING")
            stagingDir.mkdirs()
            tmpDir.resolve("RPMS").mkdirs()
            tmpDir.resolve("SPECS").mkdirs()
            tmpDir.resolve("BUILD").mkdirs()
            tmpDir.resolve("BUILDROOT").mkdirs()

            // Extract rpm contents into staging area
            exec(
                execOperations,
                "/bin/sh",
                listOf("-c", "rpm2cpio '${rpmFile.absolutePath}' | cpio -idmv"),
                workingDir = stagingDir,
            )

            // Modify .desktop files in staging
            val desktopFiles = stagingDir.walkTopDown().filter { it.isFile && it.extension == "desktop" }.toList()
            for (desktopFile in desktopFiles) {
                injectDesktopFileFields(desktopFile, startupWMClass)
                logger.lifecycle("  Injected StartupWMClass=$startupWMClass into ${desktopFile.name}")
            }

            // Query original metadata
            val name = queryRpm(execOperations, rpmFile, "%{NAME}")
            val version = queryRpm(execOperations, rpmFile, "%{VERSION}")
            val release = queryRpm(execOperations, rpmFile, "%{RELEASE}")
            val summary = queryRpm(execOperations, rpmFile, "%{SUMMARY}")
            val license = queryRpm(execOperations, rpmFile, "%{LICENSE}")
            val description = queryRpm(execOperations, rpmFile, "%{DESCRIPTION}")

            // Get existing requires
            val existingRequires =
                queryRpm(execOperations, rpmFile, "[%{REQUIRES}\\n]")
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

            // Get pre/post install scripts
            val preInstall =
                queryRpm(execOperations, rpmFile, "%{PREIN}")
                    .takeIf { it != "(none)" && it.isNotBlank() }
            val postInstall =
                queryRpm(execOperations, rpmFile, "%{POSTIN}")
                    .takeIf { it != "(none)" && it.isNotBlank() }

            // Merge requires
            val allRequires = (existingRequires + rpmRequires).distinct()

            // List all files from staging content
            val allFiles =
                stagingDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .map { "/" + it.relativeTo(stagingDir).path }
                    .toList()

            // List all directories (non-root)
            val allDirs =
                stagingDir
                    .walkTopDown()
                    .filter { it.isDirectory && it != stagingDir }
                    .map { "%dir \"/" + it.relativeTo(stagingDir).path + "\"" }
                    .toList()

            // Generate spec file — use absolute path to staging in %install
            val specFile = tmpDir.resolve("SPECS/repackage.spec")
            val specContent =
                buildString {
                    appendLine("Name: $name")
                    appendLine("Version: $version")
                    appendLine("Release: $release")
                    appendLine("Summary: $summary")
                    appendLine("License: $license")
                    appendLine("AutoReqProv: no")
                    if (allRequires.isNotEmpty()) {
                        appendLine("Requires: ${allRequires.joinToString(", ")}")
                    }
                    appendLine()
                    appendLine("%description")
                    appendLine(description)
                    appendLine()
                    appendLine("%install")
                    appendLine("mkdir -p %{buildroot}")
                    appendLine("cp -a ${stagingDir.absolutePath}/* %{buildroot}/")
                    appendLine()
                    appendLine("%files")
                    for (dir in allDirs) {
                        appendLine(dir)
                    }
                    for (file in allFiles) {
                        appendLine("\"$file\"")
                    }
                    if (preInstall != null) {
                        appendLine()
                        appendLine("%pre")
                        appendLine(preInstall)
                    }
                    if (postInstall != null) {
                        appendLine()
                        appendLine("%post")
                        appendLine(postInstall)
                    }
                }
            specFile.writeText(specContent)
            logger.lifecycle("  Generated spec: ${specFile.absolutePath}")

            // Validate compression settings
            if (compressionLevel != null) {
                require(compression != null) {
                    "rpmCompressionLevel requires rpmCompression to be set"
                }
                require(compressionLevel in 0..compression.maxLevel) {
                    "rpmCompressionLevel $compressionLevel is out of range for ${compression.name} (0..${compression.maxLevel})"
                }
            }

            // Rebuild rpm — let rpmbuild manage its own BUILDROOT
            val rpmbuildArgs = mutableListOf("-bb", "--define", "_topdir ${tmpDir.absolutePath}")
            if (compression != null) {
                val level = compressionLevel ?: compression.defaultLevel
                rpmbuildArgs.addAll(listOf("--define", "_binary_payload w${level}.${compression.payloadSuffix}"))
            }
            rpmbuildArgs.add(specFile.absolutePath)
            exec(execOperations, "rpmbuild", rpmbuildArgs)

            // Find the rebuilt rpm and replace the original
            val rpmsDir = tmpDir.resolve("RPMS")
            val rebuiltRpm =
                rpmsDir.walkTopDown().firstOrNull { it.extension == "rpm" }
                    ?: error("Failed to find rebuilt .rpm in ${rpmsDir.absolutePath}")
            rebuiltRpm.copyTo(rpmFile, overwrite = true)
            logger.lifecycle(
                "  Repacked .rpm: ${rpmFile.name}" +
                    (compression?.let { " (compression: ${it.name.lowercase()}${compressionLevel?.let { l -> ", level: $l" } ?: ""})" } ?: ""),
            )
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    internal fun injectDesktopFileFields(
        desktopFile: File,
        startupWMClass: String,
    ) {
        val lines = desktopFile.readLines().toMutableList()
        val wmClassKey = "StartupWMClass="
        val existingIndex = lines.indexOfFirst { it.startsWith(wmClassKey) }

        if (existingIndex >= 0) {
            lines[existingIndex] = "$wmClassKey$startupWMClass"
        } else {
            // Insert after the [Desktop Entry] section header
            val entryIndex = lines.indexOfFirst { it.trim() == "[Desktop Entry]" }
            val insertIndex = if (entryIndex >= 0) entryIndex + 1 else lines.size
            lines.add(insertIndex, "$wmClassKey$startupWMClass")
        }

        desktopFile.writeText(lines.joinToString("\n") + "\n")
    }

    internal fun rewriteT64Deps(deps: List<String>): List<String> =
        deps.map { dep ->
            val baseDep = dep.trim()
            val rewritten = T64_REWRITES[baseDep]
            if (rewritten != null) "$rewritten | $baseDep" else baseDep
        }

    private fun modifyDebControl(
        controlFile: File,
        extraDepends: List<String>,
        enableT64: Boolean,
        logger: Logger,
    ) {
        val lines = controlFile.readLines().toMutableList()
        val dependsIndex = lines.indexOfFirst { it.startsWith("Depends:") }

        val finalExtraDepends = if (enableT64) rewriteT64Deps(extraDepends) else extraDepends

        if (dependsIndex >= 0) {
            // Append to existing Depends line
            val existingDeps = lines[dependsIndex].removePrefix("Depends:").trim()
            val allDeps =
                if (existingDeps.isNotEmpty()) {
                    "$existingDeps, ${finalExtraDepends.joinToString(", ")}"
                } else {
                    finalExtraDepends.joinToString(", ")
                }
            lines[dependsIndex] = "Depends: $allDeps"
        } else {
            // Add Depends line after the last header field
            lines.add("Depends: ${finalExtraDepends.joinToString(", ")}")
        }

        controlFile.writeText(lines.joinToString("\n") + "\n")
        logger.lifecycle("  Updated Depends in DEBIAN/control: ${finalExtraDepends.joinToString(", ")}")
    }

    private fun queryRpm(
        execOperations: ExecOperations,
        rpmFile: File,
        queryFormat: String,
    ): String {
        val stdout = ByteArrayOutputStream()
        execOperations.exec { spec ->
            spec.executable = "rpm"
            spec.args("-qp", "--queryformat", queryFormat, rpmFile.absolutePath)
            spec.standardOutput = stdout
            spec.isIgnoreExitValue = false
        }
        return stdout.toString().trim()
    }

    private fun exec(
        execOperations: ExecOperations,
        executable: String,
        args: List<String>,
        workingDir: File? = null,
    ) {
        execOperations.exec { spec ->
            spec.executable = executable
            spec.args(args)
            if (workingDir != null) {
                spec.workingDir(workingDir)
            }
            spec.isIgnoreExitValue = false
        }
    }

    private fun createTempDir(prefix: String): File {
        val dir =
            java.nio.file.Files
                .createTempDirectory(prefix)
                .toFile()
        return dir
    }
}
