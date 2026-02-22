package io.github.kdroidfilter.nucleus.notification.builder

import io.github.kdroidfilter.nucleus.notification.model.DismissalReason

interface NotificationProvider {
    fun sendNotification(builder: NotificationBuilder)
    fun hideNotification(builder: NotificationBuilder)
}
