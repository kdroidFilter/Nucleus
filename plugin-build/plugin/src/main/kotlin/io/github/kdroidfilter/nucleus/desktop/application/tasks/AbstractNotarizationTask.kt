/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.nucleus.desktop.application.tasks

import io.github.kdroidfilter.nucleus.desktop.application.dsl.MacOSNotarizationSettings
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat
import io.github.kdroidfilter.nucleus.desktop.application.internal.files.checkExistingFile
import io.github.kdroidfilter.nucleus.desktop.application.internal.files.findOutputFileOrDir
import io.github.kdroidfilter.nucleus.desktop.application.internal.validation.ValidatedMacOSNotarizationSettings
import io.github.kdroidfilter.nucleus.desktop.application.internal.validation.validate
import io.github.kdroidfilter.nucleus.desktop.tasks.AbstractNucleusTask
import io.github.kdroidfilter.nucleus.internal.utils.MacUtils
import io.github.kdroidfilter.nucleus.internal.utils.ioFile
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

abstract class AbstractNotarizationTask
    @Inject
    constructor(
        @get:Input
        val targetFormat: TargetFormat,
    ) : AbstractNucleusTask() {
        @get:Nested
        @get:Optional
        internal var nonValidatedNotarizationSettings: MacOSNotarizationSettings? = null

        @get:InputDirectory
        val inputDir: DirectoryProperty = objects.directoryProperty()

        init {
            check(targetFormat != TargetFormat.RawAppImage) { "${TargetFormat.RawAppImage} cannot be notarized!" }
        }

        @TaskAction
        fun run() {
            val notarization = nonValidatedNotarizationSettings.validate()
            val packageFile = findOutputFileOrDir(inputDir.ioFile, targetFormat).checkExistingFile()

            notarize(notarization, packageFile)
            staple(packageFile)
            updateMetadataFiles(packageFile)
        }

        private fun notarize(
            notarization: ValidatedMacOSNotarizationSettings,
            packageFile: File,
        ) {
            logger.info("Uploading '${packageFile.name}' for notarization")
            val args =
                listOfNotNull(
                    "notarytool",
                    "submit",
                    "--wait",
                    "--apple-id",
                    notarization.appleID,
                    "--team-id",
                    notarization.teamID,
                    packageFile.absolutePath,
                )
            runExternalTool(tool = MacUtils.xcrun, args = args, stdinStr = notarization.password)
        }

        private fun staple(packageFile: File) {
            runExternalTool(
                tool = MacUtils.xcrun,
                args = listOf("stapler", "staple", packageFile.absolutePath),
            )
        }

        private fun updateMetadataFiles(packageFile: File) {
            val dir = packageFile.parentFile ?: return
            val fileName = packageFile.name
            val newSize = packageFile.length()
            val newHash = sha512Base64(packageFile)

            val ymlFiles = dir.listFiles { f -> f.extension == "yml" || f.extension == "yaml" } ?: return
            for (ymlFile in ymlFiles) {
                val content = ymlFile.readText()
                if (!content.contains(fileName)) continue

                val updated = updateYamlEntry(content, fileName, newHash, newSize)
                if (updated != content) {
                    ymlFile.writeText(updated)
                    logger.lifecycle("Updated checksums in ${ymlFile.name} for $fileName")
                }
            }
        }

        private fun sha512Base64(file: File): String {
            val digest = MessageDigest.getInstance("SHA-512")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            return Base64.getEncoder().encodeToString(digest.digest())
        }

        companion object {
            private const val DEFAULT_BUFFER_SIZE = 8192

            internal fun updateYamlEntry(
                yaml: String,
                fileName: String,
                newHash: String,
                newSize: Long,
            ): String {
                val lines = yaml.lines().toMutableList()
                var i = 0
                var topLevelPath: String? = null

                while (i < lines.size) {
                    val line = lines[i]
                    val trimmed = line.trimStart()

                    if (isUrlEntry(trimmed) && extractUrl(trimmed) == fileName) {
                        i = updateFileEntryFields(lines, i + 1, newHash, newSize)
                        continue
                    }

                    val isTopLevel = !line.startsWith(" ") && !line.startsWith("\t")
                    if (isTopLevel && trimmed.startsWith("path:")) {
                        topLevelPath = trimmed.removePrefix("path:").trim()
                    }
                    if (isTopLevel && trimmed.startsWith("sha512:") && topLevelPath == fileName) {
                        lines[i] = "sha512: $newHash"
                    }

                    i++
                }

                return lines.joinToString("\n")
            }

            private fun isUrlEntry(trimmed: String): Boolean = trimmed.startsWith("- url:") || trimmed.startsWith("-url:")

            private fun extractUrl(trimmed: String): String =
                trimmed
                    .removePrefix("-")
                    .trimStart()
                    .removePrefix("url:")
                    .trim()

            private fun isEndOfFileEntry(entryLine: String): Boolean {
                if (isUrlEntry(entryLine)) return true
                if (entryLine.startsWith("blockMapSize:")) return false
                return !entryLine.startsWith(" ") && entryLine.contains(":")
            }

            private fun updateFileEntryFields(
                lines: MutableList<String>,
                startIndex: Int,
                newHash: String,
                newSize: Long,
            ): Int {
                var i = startIndex
                while (i < lines.size) {
                    val entryLine = lines[i].trimStart()
                    if (entryLine.startsWith("sha512:")) {
                        val indent = lines[i].length - lines[i].trimStart().length
                        lines[i] = " ".repeat(indent) + "sha512: $newHash"
                    } else if (entryLine.startsWith("size:")) {
                        val indent = lines[i].length - lines[i].trimStart().length
                        lines[i] = " ".repeat(indent) + "size: $newSize"
                    } else if (isEndOfFileEntry(entryLine)) {
                        break
                    }
                    i++
                }
                return i
            }
        }
    }
