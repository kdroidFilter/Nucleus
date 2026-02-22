package io.github.kdroidfilter.nucleus.notification.mac

import io.github.kdroidfilter.nucleus.core.runtime.tools.debugln
import io.github.kdroidfilter.nucleus.core.runtime.tools.errorln
import io.github.kdroidfilter.nucleus.core.runtime.tools.infoln
import io.github.kdroidfilter.nucleus.core.runtime.tools.warnln
import io.github.kdroidfilter.nucleus.notification.builder.NotificationBuilder
import io.github.kdroidfilter.nucleus.notification.builder.NotificationProvider
import io.github.kdroidfilter.nucleus.notification.model.DismissalReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

class MacNotificationProvider : NotificationProvider {
    private val activeNotifications = ConcurrentHashMap<Int, Long>()
    private var coroutineScope: CoroutineScope? = null

    init {
        NativeNotificationBridge.load()
    }

    override fun sendNotification(builder: NotificationBuilder) {
        coroutineScope = CoroutineScope(Dispatchers.IO).also { scope ->
            scope.launch {
                try {
                    val appIconPath = builder.smallIconPath
                    infoln { "MacNotificationProvider: Sending notification with title: ${builder.title}" }

                    val notificationPtr: Long = try {
                        NativeNotificationBridge.createNotification(
                            title = builder.title,
                            body = builder.message,
                            iconPath = appIconPath
                        )
                    } catch (e: Exception) {
                        errorln { "MacNotificationProvider: Exception creating notification: ${e.message}" }
                        -1L
                    }

                    if (notificationPtr <= 0L) {
                        errorln { "MacNotificationProvider: Failed to create notification." }
                        builder.onFailed?.invoke()
                        return@launch
                    }

                    activeNotifications[builder.id] = notificationPtr

                    builder.onActivated?.let { onActivated ->
                        val callback = object : NotificationClickedCallback() {
                            override fun invoke(notificationPtr: Long, userData: Long) {
                                onActivated()
                            }
                        }
                        NativeNotificationBridge.setNotificationClickedCallback(notificationPtr, callback)
                    }

                    builder.onDismissed?.let { onDismissed ->
                        val callback = object : NotificationClosedCallback() {
                            override fun invoke(notificationPtr: Long, userData: Long) {
                                onDismissed(DismissalReason.UserCanceled)
                            }
                        }
                        NativeNotificationBridge.setNotificationClosedCallback(notificationPtr, callback)
                    }

                    builder.largeImagePath?.let { path ->
                        try {
                            val file = File(path)
                            val filePath = if (file.exists() && file.isFile) {
                                file.absolutePath
                            } else {
                                extractToTempIfDifferent(path)?.absolutePath
                            }
                            filePath?.let {
                                NativeNotificationBridge.setNotificationImage(notificationPtr, "file://$it")
                            }
                        } catch (e: Exception) {
                            warnln { "MacNotificationProvider: Exception processing large image: ${e.message}" }
                        }
                    }

                    builder.buttons.forEach { button ->
                        NativeNotificationBridge.addButtonToNotification(
                            notification = notificationPtr,
                            buttonId = button.label,
                            buttonLabel = button.label
                        )
                    }

                    val result: Int = try {
                        NativeNotificationBridge.sendNotification(notificationPtr)
                    } catch (e: Exception) {
                        errorln { "MacNotificationProvider: Exception sending notification: ${e.message}" }
                        -1
                    }

                    if (result == 0) {
                        infoln { "MacNotificationProvider: Notification sent successfully." }
                    } else {
                        errorln { "MacNotificationProvider: Failed to send notification." }
                        builder.onFailed?.invoke()
                        activeNotifications.remove(builder.id)
                        try {
                            NativeNotificationBridge.cleanupNotification(notificationPtr)
                        } catch (e: Exception) {
                            warnln { "MacNotificationProvider: Exception cleaning up notification: ${e.message}" }
                        }
                    }
                } catch (e: Exception) {
                    errorln { "MacNotificationProvider: Critical exception in sendNotification: ${e.message}" }
                    builder.onFailed?.invoke()
                }
            }
        }
    }

    override fun hideNotification(builder: NotificationBuilder) {
        try {
            val notificationPtr = activeNotifications[builder.id]
            if (notificationPtr != null) {
                try {
                    NativeNotificationBridge.hideNotification(notificationPtr)
                    infoln { "MacNotificationProvider: Notification hide called for ID: ${builder.id}" }
                    Thread.sleep(100)
                    NativeNotificationBridge.hideNotification(notificationPtr)
                } catch (e: Exception) {
                    warnln { "MacNotificationProvider: Exception hiding notification: ${e.message}" }
                }

                try {
                    NativeNotificationBridge.cleanupNotification(notificationPtr)
                } catch (e: Exception) {
                    warnln { "MacNotificationProvider: Exception cleaning up notification: ${e.message}" }
                }

                activeNotifications.remove(builder.id)
                infoln { "MacNotificationProvider: Notification hidden and cleaned up: ${builder.id}" }
            } else {
                warnln { "MacNotificationProvider: No active notification found with ID: ${builder.id}" }
            }
        } catch (e: Exception) {
            errorln { "MacNotificationProvider: Critical exception in hideNotification: ${e.message}" }
        }
    }

    private fun extractToTempIfDifferent(path: String): File? {
        return try {
            val resourceStream = MacNotificationProvider::class.java.getResourceAsStream("/$path")
                ?: return null
            val tempDir = Files.createTempDirectory("nucleus-notification")
            val tempFile = tempDir.resolve(path.substringAfterLast("/"))
            resourceStream.use { Files.copy(it, tempFile) }
            tempFile.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            tempFile.toFile()
        } catch (e: Exception) {
            null
        }
    }
}
