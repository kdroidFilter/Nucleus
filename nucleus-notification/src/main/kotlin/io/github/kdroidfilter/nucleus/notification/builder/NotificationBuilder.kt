package io.github.kdroidfilter.nucleus.notification.builder

import io.github.kdroidfilter.nucleus.notification.model.Button
import io.github.kdroidfilter.nucleus.notification.model.DismissalReason
import io.github.kdroidfilter.nucleus.notification.mac.MacNotificationProvider
import io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationProvider
import io.github.kdroidfilter.nucleus.notification.noop.NoOpNotificationProvider
import java.util.Locale

@Suppress("ExperimentalAnnotationRetention")
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This notifications API is experimental and may change in the future."
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalNotificationsApi

@ExperimentalNotificationsApi
fun notification(
    title: String = "",
    message: String = "",
    largeIcon: String? = null,
    smallIcon: String? = null,
    onActivated: (() -> Unit)? = null,
    onDismissed: ((DismissalReason) -> Unit)? = null,
    onFailed: (() -> Unit)? = null,
    builderAction: NotificationBuilder.() -> Unit = {}
): Notification {
    val builder = NotificationBuilder(title, message, largeIcon, smallIcon, onActivated, onDismissed, onFailed)
    builder.builderAction()
    return Notification(builder)
}

@ExperimentalNotificationsApi
suspend fun sendNotification(
    title: String = "",
    message: String = "",
    largeImage: String? = null,
    smallIcon: String? = null,
    onActivated: (() -> Unit)? = null,
    onDismissed: ((DismissalReason) -> Unit)? = null,
    onFailed: (() -> Unit)? = null,
    builderAction: NotificationBuilder.() -> Unit = {}
): Notification {
    val notif = notification(title, message, largeImage, smallIcon, onActivated, onDismissed, onFailed, builderAction)
    notif.send()
    return notif
}

class Notification internal constructor(private val builder: NotificationBuilder) {
    fun send() {
        val provider = getNotificationProvider()
        provider.sendNotification(builder)
    }

    fun hide() {
        val provider = getNotificationProvider()
        provider.hideNotification(builder)
    }
}

class NotificationBuilder(
    var title: String = "",
    var message: String = "",
    var largeImagePath: String?,
    var smallIconPath: String? = null,
    var onActivated: (() -> Unit)? = null,
    var onDismissed: ((DismissalReason) -> Unit)? = null,
    var onFailed: (() -> Unit)? = null,
) {
    internal val buttons = mutableListOf<Button>()
    internal val id: Int = generateUniqueId()

    companion object {
        private var lastId = 0

        private fun generateUniqueId(): Int {
            return ++lastId
        }
    }

    fun button(title: String, onClick: () -> Unit) {
        buttons.add(Button(title, onClick))
    }
}

fun getNotificationProvider(): NotificationProvider {
    val osName = System.getProperty("os.name", "").lowercase(Locale.US)
    return when {
        osName.contains("mac") || osName.contains("darwin") -> MacNotificationProvider()
        osName.contains("linux") -> LinuxNotificationProvider()
        else -> NoOpNotificationProvider()
    }
}
