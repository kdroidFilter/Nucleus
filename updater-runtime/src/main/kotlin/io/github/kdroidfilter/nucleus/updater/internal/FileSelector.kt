package io.github.kdroidfilter.nucleus.updater.internal

import io.github.kdroidfilter.nucleus.updater.Platform

internal object FileSelector {
    private val FORMAT_EXTENSIONS =
        mapOf(
            "deb" to ".deb",
            "rpm" to ".rpm",
            "appimage" to ".appimage",
            "dmg" to ".dmg",
            "pkg" to ".pkg",
            "exe" to ".exe",
            "nsis" to ".exe",
            "msi" to ".msi",
            "snap" to ".snap",
        )

    private val X64_PATTERNS = listOf("amd64", "x64", "x86_64")
    private val ARM64_PATTERNS = listOf("arm64", "aarch64")

    fun select(
        files: List<YamlFileEntry>,
        platform: Platform,
        arch: Arch,
        format: String?,
    ): YamlFileEntry? {
        if (files.isEmpty()) return null

        val extension = format?.let { FORMAT_EXTENSIONS[it.lowercase()] }
        val candidates =
            if (extension != null) {
                files.filter { it.url.lowercase().endsWith(extension) }
            } else {
                filterByPlatform(files, platform)
            }

        if (candidates.isEmpty()) return null

        // Filter by architecture
        val archFiltered = filterByArch(candidates, arch)
        if (archFiltered.isNotEmpty()) return archFiltered.first()

        // Fallback: no arch-specific match (mono-arch release)
        return candidates.first()
    }

    private fun filterByPlatform(
        files: List<YamlFileEntry>,
        platform: Platform,
    ): List<YamlFileEntry> {
        val extensions =
            when (platform) {
                Platform.WINDOWS -> listOf(".exe", ".msi")
                Platform.MACOS -> listOf(".dmg", ".pkg")
                Platform.LINUX -> listOf(".deb", ".rpm", ".appimage", ".snap")
            }
        return files.filter { file ->
            val lower = file.url.lowercase()
            extensions.any { lower.endsWith(it) }
        }
    }

    private fun filterByArch(
        files: List<YamlFileEntry>,
        arch: Arch,
    ): List<YamlFileEntry> {
        val patterns =
            when (arch) {
                Arch.X64 -> X64_PATTERNS
                Arch.ARM64 -> ARM64_PATTERNS
            }
        return files.filter { file ->
            val lower = file.url.lowercase()
            patterns.any { lower.contains(it) }
        }
    }
}
