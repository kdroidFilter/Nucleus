package io.github.kdroidfilter.nucleus.notification.linux

import io.github.kdroidfilter.nucleus.core.runtime.tools.debugln
import io.github.kdroidfilter.nucleus.core.runtime.tools.infoln
import io.github.kdroidfilter.nucleus.core.runtime.tools.warnln
import java.nio.file.Files
import java.nio.file.Path

internal object NativeNotificationBridge {
    private var loaded = false

    fun load() {
        if (loaded) return

        try {
            System.loadLibrary("nucleus_notification")
            loaded = true
            infoln { "NativeNotificationBridge (Linux): Library loaded via System.loadLibrary" }
            return
        } catch (_: UnsatisfiedLinkError) {
            debugln { "NativeNotificationBridge (Linux): System.loadLibrary failed, trying resource extraction" }
        }

        try {
            val arch = System.getProperty("os.arch").let {
                when (it) {
                    "aarch64", "arm64" -> "aarch64"
                    else -> "x64"
                }
            }
            val resourcePath = "/nucleus/native/linux-$arch/libnucleus_notification.so"
            val stream = NativeNotificationBridge::class.java.getResourceAsStream(resourcePath)
                ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-native-linux")
            val tempLib = tempDir.resolve("libnucleus_notification.so")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
            infoln { "NativeNotificationBridge (Linux): Library loaded via resource extraction" }
        } catch (e: Exception) {
            warnln {
                "NativeNotificationBridge (Linux): " +
                    "Failed to load nucleus_notification native library: ${e.message}"
            }
        }
    }

    val isLoaded: Boolean get() = loaded

    @JvmStatic
    external fun setDebugMode(enable: Int)

    @JvmStatic
    external fun init(appName: String): Int

    @JvmStatic
    external fun createNotification(
        summary: String,
        body: String,
        iconPath: String?
    ): Long

    @JvmStatic
    external fun addButtonToNotification(
        notification: Long,
        buttonId: String,
        buttonLabel: String
    )

    @JvmStatic
    external fun setNotificationClickedCallback(
        notification: Long,
        callback: NotificationClickedCallback?
    )

    @JvmStatic
    external fun setNotificationClosedCallback(
        notification: Long,
        callback: NotificationClosedCallback?
    )

    @JvmStatic
    external fun setButtonCallback(callback: ButtonClickedCallback?)

    @JvmStatic
    external fun setNotificationImage(notification: Long, imagePath: String)

    @JvmStatic
    external fun sendNotification(notification: Long): Int

    @JvmStatic
    external fun closeNotification(notification: Long): Int

    @JvmStatic
    external fun cleanupNotification(notification: Long)

    @JvmStatic
    external fun runMainLoop()

    @JvmStatic
    external fun quitMainLoop()

    @JvmStatic
    external fun cleanup()
}

abstract class NotificationClickedCallback {
    abstract fun invoke(notificationPtr: Long, userData: Long)
}

abstract class NotificationClosedCallback {
    abstract fun invoke(notificationPtr: Long, userData: Long)
}

abstract class ButtonClickedCallback {
    abstract fun invoke(notificationPtr: Long, buttonId: String, userData: Long)
}
