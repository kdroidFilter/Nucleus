/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package io.github.kdroidfilter.composedeskkit.desktop.application.internal

import java.io.File

internal object WindowsKitsLocator {
    private val versionRegex = Regex("""\d+(\.\d+){3}""")

    fun locateMakeAppx(architecture: String): File? = locateTool("makeappx.exe", architecture)

    fun locateSignTool(architecture: String): File? = locateTool("signtool.exe", architecture)

    @Suppress("ReturnCount")
    private fun locateTool(
        toolName: String,
        architecture: String,
    ): File? {
        val kitsRoot = findWindowsKitsRoot() ?: return null
        val binDir = kitsRoot.resolve("bin")

        val directCandidate = binDir.resolve(architecture).resolve(toolName)
        if (directCandidate.isFile) return directCandidate

        return binDir
            .listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && versionRegex.matches(it.name) }
            ?.sortedWith { left, right -> compareVersions(parseVersion(right.name), parseVersion(left.name)) }
            ?.map { it.resolve(architecture).resolve(toolName) }
            ?.firstOrNull { it.isFile }
    }

    private fun findWindowsKitsRoot(): File? {
        val programFiles = System.getenv("ProgramFiles(x86)") ?: System.getenv("ProgramFiles") ?: return null
        val candidates = listOf("Windows Kits/10", "Windows Kits/11")
        return candidates.asSequence().map { File(programFiles, it) }.firstOrNull { it.isDirectory }
    }

    private fun parseVersion(version: String): List<Int> = version.split('.').mapNotNull { it.toIntOrNull() }

    private fun compareVersions(
        left: List<Int>,
        right: List<Int>,
    ): Int {
        val maxSize = maxOf(left.size, right.size)
        for (index in 0 until maxSize) {
            val leftValue = left.getOrElse(index) { 0 }
            val rightValue = right.getOrElse(index) { 0 }
            if (leftValue != rightValue) return leftValue.compareTo(rightValue)
        }
        return 0
    }
}
