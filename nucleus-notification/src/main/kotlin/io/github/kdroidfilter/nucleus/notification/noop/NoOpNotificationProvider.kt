package io.github.kdroidfilter.nucleus.notification.noop

import io.github.kdroidfilter.nucleus.notification.builder.NotificationBuilder
import io.github.kdroidfilter.nucleus.notification.builder.NotificationProvider

class NoOpNotificationProvider : NotificationProvider {
    override fun sendNotification(builder: NotificationBuilder) {
        builder.onFailed?.invoke()
    }

    override fun hideNotification(builder: NotificationBuilder) {
    }
}
