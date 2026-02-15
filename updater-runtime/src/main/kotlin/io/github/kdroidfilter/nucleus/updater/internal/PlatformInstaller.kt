package io.github.kdroidfilter.nucleus.updater.internal

import io.github.kdroidfilter.nucleus.updater.Platform
import java.io.File
import kotlin.system.exitProcess

internal object PlatformInstaller {
    fun install(
        file: File,
        platform: Platform,
    ) {
        val extension = file.name.substringAfterLast('.').lowercase()
        val process = buildProcessForInstaller(file, platform, extension)
        process.start()
        exitProcess(0)
    }

    private fun buildProcessForInstaller(
        file: File,
        platform: Platform,
        extension: String,
    ): ProcessBuilder =
        when (platform) {
            Platform.LINUX -> buildLinuxInstaller(file, extension)
            Platform.MACOS -> buildMacInstaller(file)
            Platform.WINDOWS -> buildWindowsInstaller(file, extension)
        }

    private fun buildLinuxInstaller(
        file: File,
        extension: String,
    ): ProcessBuilder =
        when (extension) {
            "deb" -> ProcessBuilder("sudo", "dpkg", "-i", file.absolutePath)
            "rpm" -> ProcessBuilder("sudo", "rpm", "-U", file.absolutePath)
            "appimage" -> {
                file.setExecutable(true)
                ProcessBuilder(file.absolutePath)
            }
            else -> ProcessBuilder("xdg-open", file.absolutePath)
        }

    private fun buildMacInstaller(file: File): ProcessBuilder = ProcessBuilder("open", file.absolutePath)

    private fun buildWindowsInstaller(
        file: File,
        extension: String,
    ): ProcessBuilder =
        when (extension) {
            "msi" -> ProcessBuilder("msiexec", "/i", file.absolutePath, "/passive")
            else -> ProcessBuilder(file.absolutePath, "/S")
        }
}
