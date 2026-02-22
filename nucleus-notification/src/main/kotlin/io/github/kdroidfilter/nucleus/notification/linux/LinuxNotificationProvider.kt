package io.github.kdroidfilter.nucleus.notification.linux

import io.github.kdroidfilter.nucleus.core.runtime.tools.debugln
import io.github.kdroidfilter.nucleus.core.runtime.tools.errorln
import io.github.kdroidfilter.nucleus.core.runtime.tools.infoln
import io.github.kdroidfilter.nucleus.core.runtime.tools.warnln
import io.github.kdroidfilter.nucleus.notification.builder.NotificationBuilder
import io.github.kdroidfilter.nucleus.notification.builder.NotificationProvider
import io.github.kdroidfilter.nucleus.notification.model.DismissalReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class LinuxNotificationProvider : NotificationProvider {
    // Use AtomicBoolean for thread-safe access
    private val isMainLoopRunning = AtomicBoolean(false)
    
    // Single coroutine scope - reuse and manage properly
    private var coroutineScope: CoroutineScope? = null
    
    private val debugMode: Boolean = true
    
    // Track active notifications: builder.id -> notificationPtr
    private val activeNotifications = ConcurrentHashMap<Int, Long>()
    
    // Reverse lookup: notificationPtr -> builder.id
    private val notificationPtrToId = ConcurrentHashMap<Long, Int>()
    
    // Track if notification was closed by user interaction (click/button) vs natural dismiss
    private val userInteractedNotifications = ConcurrentHashMap<Int, Boolean>()
    
    // Map notification ID to button callbacks: builder.id -> (buttonLabel -> callback)
    private val buttonCallbacks = ConcurrentHashMap<Int, MutableMap<String, () -> Unit>>()
    
    // Store onActivated callbacks: builder.id -> callback
    private val activatedCallbacks = ConcurrentHashMap<Int, () -> Unit>()
    
    // Store onDismissed callbacks: builder.id -> callback
    private val dismissedCallbacks = ConcurrentHashMap<Int, (DismissalReason) -> Unit>()

    init {
        NativeNotificationBridge.load()
        if (debugMode) {
            NativeNotificationBridge.setDebugMode(1)
        }
        
        // Set up the global button callback handler
        val buttonCallback = object : ButtonClickedCallback() {
            override fun invoke(notificationPtr: Long, buttonId: String, userData: Long) {
                infoln { "LinuxNotificationProvider: Button clicked: $buttonId (ptr: $notificationPtr)" }
                // Find the correct builder ID using notification pointer
                val builderId = notificationPtrToId[notificationPtr]
                if (builderId != null) {
                    userInteractedNotifications[builderId] = true
                    // Look up callback for this specific notification
                    buttonCallbacks[builderId]?.get(buttonId)?.invoke()
                } else {
                    warnln { "LinuxNotificationProvider: Could not find builder ID for notification ptr: $notificationPtr" }
                }
            }
        }
        NativeNotificationBridge.setButtonCallback(buttonCallback)
        
        // Set up closed callback (clicked callback is set per-notification in sendNotification)
        val closedCallback = object : NotificationClosedCallback() {
            override fun invoke(notificationPtr: Long, userData: Long) {
                infoln { "LinuxNotificationProvider: Notification closed (ptr: $notificationPtr)" }
                val builderId = notificationPtrToId[notificationPtr]
                if (builderId != null) {
                    if (userInteractedNotifications[builderId] == true) {
                        infoln { "LinuxNotificationProvider: Closed by user interaction - NOT calling onDismissed" }
                    } else {
                        infoln { "LinuxNotificationProvider: Closed naturally - calling onDismissed" }
                        // Call onDismissed callback
                        dismissedCallbacks[builderId]?.invoke(DismissalReason.UserCanceled)
                    }
                    // Cleanup
                    activeNotifications.remove(builderId)
                    notificationPtrToId.remove(notificationPtr)
                    userInteractedNotifications.remove(builderId)
                    buttonCallbacks.remove(builderId)
                    activatedCallbacks.remove(builderId)
                    dismissedCallbacks.remove(builderId)
                    
                    // Stop main loop if no more active notifications
                    if (activeNotifications.isEmpty()) {
                        stopMainLoop()
                    }
                }
            }
        }
        // Note: callback is set per-notification to pass the correct notificationPtr
    }

    private fun getOrCreateCoroutineScope(): CoroutineScope {
        return coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also {
            coroutineScope = it
        }
    }

    override fun sendNotification(builder: NotificationBuilder) {
        // Reuse existing scope or create new one - don't cancel to avoid interrupting active notifications
        val scope = getOrCreateCoroutineScope()
        
        scope.launch {
            try {
                val appIconPath = builder.smallIconPath
                infoln { "LinuxNotificationProvider: Sending notification with title: ${builder.title}" }

                // Initialize the notification system
                val appName = "Nucleus"
                if (NativeNotificationBridge.init(appName) == 0) {
                    errorln { "LinuxNotificationProvider: Failed to initialize notifications." }
                    builder.onFailed?.invoke()
                    return@launch
                }

                val notificationPtr: Long = try {
                    NativeNotificationBridge.createNotification(
                        summary = builder.title,
                        body = builder.message,
                        iconPath = appIconPath
                    )
                } catch (e: Exception) {
                    errorln { "LinuxNotificationProvider: Exception creating notification: ${e.message}" }
                    -1L
                }

                if (notificationPtr <= 0L) {
                    errorln { "LinuxNotificationProvider: Failed to create notification." }
                    builder.onFailed?.invoke()
                    return@launch
                }

                // Track the notification
                activeNotifications[builder.id] = notificationPtr
                notificationPtrToId[notificationPtr] = builder.id
                userInteractedNotifications[builder.id] = false

                // Store onActivated callback
                builder.onActivated?.let { activatedCallbacks[builder.id] = it }
                
                // Store onDismissed callback
                builder.onDismissed?.let { dismissedCallbacks[builder.id] = it }

                // Set clicked callback for this notification
                if (builder.onActivated != null) {
                    val clickedCallback = object : NotificationClickedCallback() {
                        override fun invoke(notificationPtr: Long, userData: Long) {
                            userInteractedNotifications[builder.id] = true
                            builder.onActivated?.invoke()
                        }
                    }
                    NativeNotificationBridge.setNotificationClickedCallback(notificationPtr, clickedCallback)
                }

                // Set closed callback for this notification
                if (builder.onDismissed != null) {
                    val closedCallback = object : NotificationClosedCallback() {
                        override fun invoke(notificationPtr: Long, userData: Long) {
                            if (userInteractedNotifications[builder.id] == true) {
                                infoln { "LinuxNotificationProvider: Closed by user interaction - NOT calling onDismissed" }
                            } else {
                                infoln { "LinuxNotificationProvider: Closed naturally - calling onDismissed" }
                                builder.onDismissed?.invoke(DismissalReason.UserCanceled)
                            }
                        }
                    }
                    NativeNotificationBridge.setNotificationClosedCallback(notificationPtr, closedCallback)
                }

                // Set large image
                builder.largeImagePath?.let { path ->
                    try {
                        val file = File(path)
                        val filePath = if (file.exists() && file.isFile) {
                            file.absolutePath
                        } else {
                            extractToTempIfDifferent(path)?.absolutePath
                        }
                        filePath?.let {
                            NativeNotificationBridge.setNotificationImage(notificationPtr, it)
                        }
                    } catch (e: Exception) {
                        warnln { "LinuxNotificationProvider: Exception processing large image: ${e.message}" }
                    }
                }

                // Add buttons - store callbacks for this specific notification
                val buttonMap = mutableMapOf<String, () -> Unit>()
                builder.buttons.forEach { button ->
                    buttonMap[button.label] = button.onClick
                    NativeNotificationBridge.addButtonToNotification(
                        notification = notificationPtr,
                        buttonId = button.label,
                        buttonLabel = button.label
                    )
                }
                buttonCallbacks[builder.id] = buttonMap

                // Send notification
                val result: Int = try {
                    NativeNotificationBridge.sendNotification(notificationPtr)
                } catch (e: Exception) {
                    errorln { "LinuxNotificationProvider: Exception sending notification: ${e.message}" }
                    -1
                }

                if (result == 0) {
                    infoln { "LinuxNotificationProvider: Notification sent successfully." }
                    startMainLoop()
                } else {
                    errorln { "LinuxNotificationProvider: Failed to send notification." }
                    builder.onFailed?.invoke()
                    cleanupNotification(builder.id, notificationPtr)
                }
            } catch (e: Exception) {
                errorln { "LinuxNotificationProvider: Critical exception in sendNotification: ${e.message}" }
                builder.onFailed?.invoke()
            }
        }
    }

    override fun hideNotification(builder: NotificationBuilder) {
        try {
            val notificationPtr = activeNotifications[builder.id]
            if (notificationPtr != null) {
                try {
                    NativeNotificationBridge.closeNotification(notificationPtr)
                    infoln { "LinuxNotificationProvider: Notification hide called for ID: ${builder.id}" }
                } catch (e: Exception) {
                    warnln { "LinuxNotificationProvider: Exception hiding notification: ${e.message}" }
                }

                cleanupNotification(builder.id, notificationPtr)
            } else {
                warnln { "LinuxNotificationProvider: No active notification found with ID: ${builder.id}" }
            }
        } catch (e: Exception) {
            errorln { "LinuxNotificationProvider: Critical exception in hideNotification: ${e.message}" }
        }
    }

    private fun cleanupNotification(builderId: Int, notificationPtr: Long) {
        try {
            NativeNotificationBridge.cleanupNotification(notificationPtr)
        } catch (e: Exception) {
            warnln { "LinuxNotificationProvider: Exception cleaning up notification: ${e.message}" }
        }

        activeNotifications.remove(builderId)
        notificationPtrToId.remove(notificationPtr)
        userInteractedNotifications.remove(builderId)
        buttonCallbacks.remove(builderId)
        activatedCallbacks.remove(builderId)
        dismissedCallbacks.remove(builderId)
        infoln { "LinuxNotificationProvider: Notification cleaned up: $builderId" }

        // Stop main loop if no more active notifications
        if (activeNotifications.isEmpty()) {
            stopMainLoop()
        }
    }

    private fun startMainLoop() {
        // Use atomic compare-and-set to avoid race condition
        if (isMainLoopRunning.compareAndSet(false, true)) {
            infoln { "LinuxNotificationProvider: Starting main loop..." }
            Thread {
                try {
                    NativeNotificationBridge.runMainLoop()
                } catch (e: Exception) {
                    warnln { "LinuxNotificationProvider: Exception in main loop: ${e.message}" }
                } finally {
                    isMainLoopRunning.set(false)
                }
            }.apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun stopMainLoop() {
        if (isMainLoopRunning.compareAndSet(true, false)) {
            infoln { "LinuxNotificationProvider: Stopping main loop..." }
            try {
                NativeNotificationBridge.quitMainLoop()
            } catch (e: Exception) {
                warnln { "LinuxNotificationProvider: Exception stopping main loop: ${e.message}" }
            }
            coroutineScope?.cancel()
            coroutineScope = null
            try {
                NativeNotificationBridge.cleanup()
            } catch (e: Exception) {
                warnln { "LinuxNotificationProvider: Exception during cleanup: ${e.message}" }
            }
        }
    }

    private fun extractToTempIfDifferent(path: String): File? {
        return try {
            val resourceStream = LinuxNotificationProvider::class.java.getResourceAsStream("/$path")
                ?: return null
            val tempDir = Files.createTempDirectory("nucleus-notification-linux")
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
