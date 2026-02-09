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
        appName: String,
        linuxPackageName: String?,
        packageDescription: String?,
        linuxAppCategory: String?,
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

            // Create .desktop file if missing (jpackage --app-image mode does not generate one),
            // then enforce StartupWMClass on all desktop entries.
            val desktopFiles =
                ensureDesktopFiles(
                    packageRoot = tmpDir,
                    appName = appName,
                    linuxPackageName = linuxPackageName,
                    packageDescription = packageDescription,
                    linuxAppCategory = linuxAppCategory,
                    startupWMClass = startupWMClass,
                    logger = logger,
                )
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
        appName: String,
        linuxPackageName: String?,
        packageDescription: String?,
        linuxAppCategory: String?,
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

            // Create .desktop file if missing (jpackage --app-image mode does not generate one),
            // then enforce StartupWMClass on all desktop entries.
            val desktopFiles =
                ensureDesktopFiles(
                    packageRoot = stagingDir,
                    appName = appName,
                    linuxPackageName = linuxPackageName,
                    packageDescription = packageDescription,
                    linuxAppCategory = linuxAppCategory,
                    startupWMClass = startupWMClass,
                    logger = logger,
                )
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
                rpmbuildArgs.addAll(listOf("--define", "_binary_payload w$level.${compression.payloadSuffix}"))
            }
            rpmbuildArgs.add(specFile.absolutePath)
            exec(execOperations, "rpmbuild", rpmbuildArgs)

            // Find the rebuilt rpm and replace the original
            val rpmsDir = tmpDir.resolve("RPMS")
            val rebuiltRpm =
                rpmsDir.walkTopDown().firstOrNull { it.extension == "rpm" }
                    ?: error("Failed to find rebuilt .rpm in ${rpmsDir.absolutePath}")
            rebuiltRpm.copyTo(rpmFile, overwrite = true)
            val compressionInfo =
                compression
                    ?.let {
                        val levelInfo = compressionLevel?.let { level -> ", level: $level" }.orEmpty()
                        " (compression: ${it.name.lowercase()}$levelInfo)"
                    }.orEmpty()
            logger.lifecycle(
                "  Repacked .rpm: ${rpmFile.name}$compressionInfo",
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

    private fun ensureDesktopFiles(
        packageRoot: File,
        appName: String,
        linuxPackageName: String?,
        packageDescription: String?,
        linuxAppCategory: String?,
        startupWMClass: String,
        logger: Logger,
    ): List<File> {
        val existingDesktopFiles = findDesktopFiles(packageRoot)
        if (existingDesktopFiles.isNotEmpty()) {
            return existingDesktopFiles
        }

        val createdDesktopFile =
            createDesktopFile(
                packageRoot = packageRoot,
                appName = appName,
                linuxPackageName = linuxPackageName,
                packageDescription = packageDescription,
                linuxAppCategory = linuxAppCategory,
                startupWMClass = startupWMClass,
                logger = logger,
            )
        return listOf(createdDesktopFile)
    }

    private fun findDesktopFiles(packageRoot: File): List<File> =
        packageRoot
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("desktop", ignoreCase = true) }
            .toList()

    private fun createDesktopFile(
        packageRoot: File,
        appName: String,
        linuxPackageName: String?,
        packageDescription: String?,
        linuxAppCategory: String?,
        startupWMClass: String,
        logger: Logger,
    ): File {
        val effectivePackageName =
            linuxPackageName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: appName.lowercase()

        val desktopDir = packageRoot.resolve("usr/share/applications")
        desktopDir.mkdirs()

        val desktopFileName =
            "${sanitizeDesktopToken(effectivePackageName)}-${sanitizeDesktopToken(appName)}.desktop"
        val desktopFile = desktopDir.resolve(desktopFileName)

        val execPath = detectExecPath(packageRoot, effectivePackageName, appName)
        val iconPath = detectIconPath(packageRoot, effectivePackageName, appName)
        val categories = normalizeDesktopCategories(linuxAppCategory)
        val comment =
            packageDescription
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: appName

        val desktopContent =
            buildString {
                appendLine("[Desktop Entry]")
                appendLine("Name=$appName")
                appendLine("Comment=$comment")
                appendLine("Exec=$execPath %U")
                appendLine("Icon=$iconPath")
                appendLine("Terminal=false")
                appendLine("Type=Application")
                appendLine("Categories=$categories")
                appendLine("StartupWMClass=$startupWMClass")
            }
        desktopFile.writeText(desktopContent)

        logger.lifecycle("  Created .desktop file: ${desktopFile.relativeTo(packageRoot).path}")
        return desktopFile
    }

    private fun sanitizeDesktopToken(value: String): String =
        value
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-')
            .ifEmpty { "app" }

    private fun detectExecPath(
        packageRoot: File,
        linuxPackageName: String,
        appName: String,
    ): String {
        val preferredExec = "/opt/$linuxPackageName/bin/$appName"
        if (packageRoot.resolve(preferredExec.removePrefix("/")).isFile) {
            return preferredExec
        }

        val executableCandidates =
            packageRoot
                .walkTopDown()
                .filter { it.isFile }
                .map { "/" + it.relativeTo(packageRoot).path.replace(File.separatorChar, '/') }
                .filter { it.contains("/bin/") }
                .toList()

        return executableCandidates.firstOrNull { it.endsWith("/$appName") }
            ?: executableCandidates.firstOrNull { it.contains("/opt/$linuxPackageName/bin/") }
            ?: executableCandidates.firstOrNull()
            ?: preferredExec
    }

    private fun detectIconPath(
        packageRoot: File,
        linuxPackageName: String,
        appName: String,
    ): String {
        val preferredIcon = "/opt/$linuxPackageName/lib/$appName.png"
        if (packageRoot.resolve(preferredIcon.removePrefix("/")).isFile) {
            return preferredIcon
        }

        val iconExtensions = setOf("png", "svg", "xpm", "ico")
        val iconCandidates =
            packageRoot
                .walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in iconExtensions }
                .map { "/" + it.relativeTo(packageRoot).path.replace(File.separatorChar, '/') }
                .toList()

        return iconCandidates.firstOrNull {
            it.substringAfterLast('/').substringBeforeLast('.').equals(appName, ignoreCase = true)
        } ?: iconCandidates.firstOrNull { it.contains("/opt/$linuxPackageName/lib/") }
            ?: iconCandidates.firstOrNull { it.contains("/usr/share/pixmaps/") }
            ?: iconCandidates.firstOrNull()
            ?: preferredIcon
    }

    private fun normalizeDesktopCategories(linuxAppCategory: String?): String {
        val category =
            linuxAppCategory
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Utility"
        return if (category.endsWith(";")) category else "$category;"
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
