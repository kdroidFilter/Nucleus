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
            check(targetFormat != TargetFormat.AppImage) { "${TargetFormat.AppImage} cannot be notarized!" }
        }

        @TaskAction
        fun run() {
            val notarization = nonValidatedNotarizationSettings.validate()
            val packageFile = findOutputFileOrDir(inputDir.ioFile, targetFormat).checkExistingFile()

            notarize(notarization, packageFile)
            staple(packageFile)
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
    }
