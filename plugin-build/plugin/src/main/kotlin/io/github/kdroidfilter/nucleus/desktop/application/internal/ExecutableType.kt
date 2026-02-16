/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:Suppress("ktlint:standard:filename")

package io.github.kdroidfilter.nucleus.desktop.application.internal

import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat
import org.gradle.api.logging.Logger
import java.io.File

internal const val EXECUTABLE_TYPE_DEV = "dev"
private const val JAVA_OPTIONS_SECTION = "[JavaOptions]"
private const val JAVA_OPTIONS_PREFIX = "java-options="
private const val EXECUTABLE_TYPE_OPTION_PREFIX = "$JAVA_OPTIONS_PREFIX-D$APP_EXECUTABLE_TYPE="

internal val TargetFormat.executableTypeValue: String
    get() =
        when (this) {
            TargetFormat.Exe -> "exe"
            TargetFormat.Msi -> "msi"
            TargetFormat.Dmg -> "dmg"
            TargetFormat.Pkg -> "pkg"
            TargetFormat.Deb -> "deb"
            TargetFormat.Rpm -> "rpm"
            TargetFormat.RawAppImage -> EXECUTABLE_TYPE_DEV
            TargetFormat.AppImage -> "appimage"
            TargetFormat.Nsis -> "nsis"
            TargetFormat.NsisWeb -> "nsis-web"
            TargetFormat.Portable -> "portable"
            TargetFormat.AppX -> "appx"
            TargetFormat.Snap -> "snap"
            TargetFormat.Flatpak -> "flatpak"
            TargetFormat.Zip -> "zip"
            TargetFormat.Tar -> "tar"
            TargetFormat.SevenZ -> "7z"
        }

internal fun updateExecutableTypeInAppImage(
    appImageDir: File,
    targetFormat: TargetFormat,
    logger: Logger,
) {
    val cfgFiles =
        appImageDir
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("cfg", ignoreCase = true) && it.name != "jvm.cfg" }
            .toList()

    if (cfgFiles.isEmpty()) {
        logger.warn("No .cfg launcher file found in app image at ${appImageDir.absolutePath}")
        return
    }

    cfgFiles.forEach { cfgFile ->
        updateExecutableTypeInCfg(cfgFile, targetFormat.executableTypeValue)
    }
}

private fun updateExecutableTypeInCfg(
    cfgFile: File,
    executableType: String,
) {
    val updatedOption = "$JAVA_OPTIONS_PREFIX-D$APP_EXECUTABLE_TYPE=$executableType"
    val lines = cfgFile.readLines().toMutableList()
    var inJavaOptions = false
    var javaOptionsSectionIndex = -1
    var replaced = false

    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()
        when {
            trimmed == JAVA_OPTIONS_SECTION -> {
                inJavaOptions = true
                if (javaOptionsSectionIndex == -1) {
                    javaOptionsSectionIndex = index
                }
            }
            trimmed.startsWith("[") -> inJavaOptions = false
            inJavaOptions && trimmed.startsWith(EXECUTABLE_TYPE_OPTION_PREFIX) && !replaced -> {
                lines[index] = updatedOption
                replaced = true
            }
        }
    }

    if (!replaced) {
        if (javaOptionsSectionIndex == -1) {
            lines.add(JAVA_OPTIONS_SECTION)
            lines.add(updatedOption)
        } else {
            lines.add(javaOptionsSectionIndex + 1, updatedOption)
        }
    }

    cfgFile.writeText(lines.joinToString(System.lineSeparator()))
}
