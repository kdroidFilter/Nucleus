package io.github.kdroidfilter.nucleus.notification.mac

import io.github.kdroidfilter.nucleus.core.runtime.tools.debugln
import io.github.kdroidfilter.nucleus.core.runtime.tools.infoln
import io.github.kdroidfilter.nucleus.core.runtime.tools.warnln
import java.nio.file.Files

internal object NativeNotificationBridge {
    private var loaded = false

    fun load() {
        if (loaded) return

        try {
            System.loadLibrary("nucleus_notification")
            loaded = true
            infoln { "NativeNotificationBridge: Library loaded via System.loadLibrary" }
            return
        } catch (_: UnsatisfiedLinkError) {
            debugln { "NativeNotificationBridge: System.loadLibrary failed, trying resource extraction" }
        }

        try {
            val arch = System.getProperty("os.arch").let {
                if (it == "aarch64" || it == "arm64") "aarch64" else "x64"
            }
            val resourcePath = "/nucleus/native/darwin-$arch/libnucleus_notification.dylib"
            val stream = NativeNotificationBridge::class.java.getResourceAsStream(resourcePath)
                ?: throw UnsatisfiedLinkError("Native library not found in JAR at $resourcePath")
            val tempDir = Files.createTempDirectory("nucleus-native")
            val tempLib = tempDir.resolve("libnucleus_notification.dylib")
            stream.use { Files.copy(it, tempLib) }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
            infoln { "NativeNotificationBridge: Library loaded via resource extraction" }
        } catch (e: Exception) {
            warnln { "NativeNotificationBridge: Failed to load nucleus_notification native library: ${e.message}" }
        }
    }

    val isLoaded: Boolean get() = loaded

    @JvmStatic
    external fun createNotification(title: String, body: String, iconPath: String?): Long

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
    external fun setNotificationImage(notification: Long, imagePath: String)

    @JvmStatic
    external fun sendNotification(notification: Long): Int

    @JvmStatic
    external fun hideNotification(notification: Long)

    @JvmStatic
    external fun cleanupNotification(notification: Long)
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
