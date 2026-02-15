package io.github.kdroidfilter.nucleus.updater.internal

import io.github.kdroidfilter.nucleus.updater.Platform

internal enum class Arch {
    X64,
    ARM64,
}

internal object PlatformInfo {
    fun currentPlatform(): Platform {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> Platform.WINDOWS
            osName.contains("mac") || osName.contains("darwin") -> Platform.MACOS
            else -> Platform.LINUX
        }
    }

    fun currentArch(): Arch {
        val osArch = System.getProperty("os.arch").lowercase()
        return when {
            osArch.contains("aarch64") || osArch.contains("arm64") -> Arch.ARM64
            else -> Arch.X64
        }
    }

    fun ymlSuffix(): String =
        when (currentPlatform()) {
            Platform.WINDOWS -> ""
            Platform.MACOS -> "mac"
            Platform.LINUX -> "linux"
        }

    fun ymlFileName(channel: String): String {
        val suffix = ymlSuffix()
        return if (suffix.isEmpty()) "$channel.yml" else "$channel-$suffix.yml"
    }
}
