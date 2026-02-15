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

        when {
            platform == Platform.MACOS && extension == "zip" -> installMacZip(file)
            platform == Platform.WINDOWS -> installWindows(file, extension)
            platform == Platform.LINUX && extension == "appimage" -> installLinuxAppImage(file)
            platform == Platform.LINUX && (extension == "deb" || extension == "rpm") -> installLinuxPackage(file, extension)
            else -> buildProcessForInstaller(file, platform, extension).start()
        }
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
            Platform.WINDOWS -> error("Windows uses installWindows()")
        }

    private fun buildLinuxInstaller(
        file: File,
        extension: String,
    ): ProcessBuilder =
        when (extension) {
            "deb" -> ProcessBuilder("sudo", "dpkg", "-i", file.absolutePath)
            "rpm" -> ProcessBuilder("sudo", "rpm", "-U", file.absolutePath)
            else -> ProcessBuilder("xdg-open", file.absolutePath)
        }

    private fun installLinuxAppImage(newAppImage: File) {
        val pid = ProcessHandle.current().pid()
        val currentAppImage = System.getenv("APPIMAGE")
            ?: error("APPIMAGE environment variable not set — update is only supported from a packaged AppImage")

        val script = File(System.getProperty("java.io.tmpdir"), "nucleus-update.sh")
        script.writeText(
            """
            |#!/usr/bin/env bash
            |set -e
            |
            |# Ignore SIGHUP to survive parent process exit
            |trap '' HUP
            |
            |NEW_FILE="${newAppImage.absolutePath}"
            |OLD_FILE="$currentAppImage"
            |APP_PID=$pid
            |
            |# Wait for the app process to fully exit
            |while kill -0 "${'$'}APP_PID" 2>/dev/null; do
            |    sleep 0.5
            |done
            |
            |# Wait for the AppImage FUSE mount to fully clean up
            |sleep 1
            |
            |# Replace the old AppImage with the new one
            |mv -f "${'$'}NEW_FILE" "${'$'}OLD_FILE"
            |chmod +x "${'$'}OLD_FILE"
            |
            |# Relaunch in a fully detached process
            |nohup "${'$'}OLD_FILE" > /dev/null 2>&1 &
            |
            |# Clean up this script
            |rm -f "${'$'}{0}"
            """.trimMargin()
        )
        script.setExecutable(true)

        // Use setsid to start the script in a new session, fully detached
        // from the current process tree
        ProcessBuilder("setsid", "bash", script.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    private fun installLinuxPackage(packageFile: File, extension: String) {
        val pid = ProcessHandle.current().pid()
        val launcher = resolveLinuxLauncher()
            ?: error("Cannot resolve application launcher from java.home")

        val installCmd = when (extension) {
            "deb" -> "pkexec dpkg -i \"\$PKG_FILE\""
            "rpm" -> "pkexec rpm -U \"\$PKG_FILE\""
            else -> error("Unsupported package format: $extension")
        }

        val script = File(System.getProperty("java.io.tmpdir"), "nucleus-update.sh")
        script.writeText(
            """
            |#!/usr/bin/env bash
            |set -e
            |
            |# Ignore SIGHUP to survive parent process exit
            |trap '' HUP
            |
            |PKG_FILE="${packageFile.absolutePath}"
            |APP_PID=$pid
            |APP_LAUNCHER="${launcher.absolutePath}"
            |
            |# Wait for the app process to fully exit
            |while kill -0 "${'$'}APP_PID" 2>/dev/null; do
            |    sleep 0.5
            |done
            |
            |sleep 1
            |
            |# Install the package (shows graphical authentication dialog)
            |$installCmd
            |
            |# Clean up the package file
            |rm -f "${'$'}PKG_FILE"
            |
            |# Relaunch the application
            |nohup "${'$'}APP_LAUNCHER" > /dev/null 2>&1 &
            |
            |# Clean up this script
            |rm -f "${'$'}{0}"
            """.trimMargin()
        )
        script.setExecutable(true)

        ProcessBuilder("setsid", "bash", script.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    /**
     * Resolves the jpackage launcher on Linux.
     * jpackage structure: /opt/<app>/bin/<Launcher> with java.home = /opt/<app>/lib/runtime
     */
    private fun resolveLinuxLauncher(): File? {
        val javaHome = System.getProperty("java.home") ?: return null
        // java.home = /opt/<app>/lib/runtime → parent = lib → parent = /opt/<app>
        val appRoot = File(javaHome).parentFile?.parentFile ?: return null
        val binDir = File(appRoot, "bin")
        if (!binDir.isDirectory) return null
        return binDir.listFiles()?.firstOrNull { it.canExecute() }
    }

    private fun buildMacInstaller(file: File): ProcessBuilder = ProcessBuilder("open", file.absolutePath)

    private fun installMacZip(zipFile: File) {
        val appBundle = resolveCurrentAppBundle()
            ?: error("Cannot resolve current .app bundle from java.home")
        val installDir = appBundle.parentFile
        val appName = appBundle.name
        val appPath = File(installDir, appName).absolutePath
        val pid = ProcessHandle.current().pid()

        // Write a shell script that will:
        // 1. Wait for our process to actually die
        // 2. Replace the app bundle
        // 3. Remove quarantine and relaunch
        val script = File(System.getProperty("java.io.tmpdir"), "nucleus-update.sh")
        script.writeText(
            """
            |#!/usr/bin/env bash
            |set -e
            |
            |ZIP_FILE="${zipFile.absolutePath}"
            |APP_PATH="${appPath}"
            |INSTALL_DIR="${installDir.absolutePath}"
            |APP_PID=$pid
            |
            |# Wait for the app process to fully exit
            |while kill -0 "${'$'}APP_PID" 2>/dev/null; do
            |    sleep 0.5
            |done
            |
            |# Remove old app bundle
            |if [ -d "${'$'}APP_PATH" ]; then
            |    rm -rf "${'$'}APP_PATH"
            |fi
            |
            |# Extract the ZIP
            |ditto -x -k "${'$'}ZIP_FILE" "${'$'}INSTALL_DIR"
            |
            |# Remove quarantine attribute
            |xattr -r -d com.apple.quarantine "${'$'}APP_PATH" 2>/dev/null || true
            |
            |# Relaunch the app
            |open "${'$'}APP_PATH"
            |
            |# Clean up
            |rm -f "${'$'}ZIP_FILE"
            |rm -f "${'$'}{0}"
            """.trimMargin()
        )
        script.setExecutable(true)

        // Launch the script as a detached process that survives our exit
        ProcessBuilder("bash", script.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        // exitProcess(0) is called by install() right after this returns
    }

    private fun resolveCurrentAppBundle(): File? {
        val javaHome = System.getProperty("java.home") ?: return null
        var dir = File(javaHome)
        while (dir.parentFile != null) {
            if (dir.name.endsWith(".app")) return dir
            dir = dir.parentFile
        }
        return null
    }

    private fun installWindows(file: File, extension: String) {
        val pid = ProcessHandle.current().pid()
        val installerCmd = when (extension) {
            "msi" -> "Start-Process msiexec -ArgumentList '/i', '\"${file.absolutePath}\"', '/passive' -Wait"
            else -> "Start-Process '${file.absolutePath}' -ArgumentList '/S', '--updated' -Wait"
        }

        val script = File(System.getProperty("java.io.tmpdir"), "nucleus-update.ps1")
        script.writeText(
            """
            |# Wait for the app process to fully exit
            |while (Get-Process -Id $pid -ErrorAction SilentlyContinue) {
            |    Start-Sleep -Milliseconds 500
            |}
            |
            |# Run the installer silently
            |$installerCmd
            |
            |# Clean up
            |Remove-Item '${file.absolutePath}' -Force -ErrorAction SilentlyContinue
            |Remove-Item '${script.absolutePath}' -Force -ErrorAction SilentlyContinue
            """.trimMargin()
        )

        ProcessBuilder(
            "powershell", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.absolutePath
        )
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }
}
