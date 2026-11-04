package ru.yetidrive.launcher

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class YetiNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        notifyDashboard()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == YANDEX_MUSIC_PACKAGE) notifyDashboard()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == YANDEX_MUSIC_PACKAGE) notifyDashboard()
    }

    private fun notifyDashboard() {
        sendBroadcast(Intent(ACTION_MEDIA_CHANGED).setPackage(packageName))
    }

    companion object {
        const val ACTION_MEDIA_CHANGED = "ru.yetidrive.launcher.MEDIA_CHANGED"
        private const val YANDEX_MUSIC_PACKAGE = "ru.yandex.music"
    }
}
