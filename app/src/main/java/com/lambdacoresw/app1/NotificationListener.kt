package com.lambdacoresw.app1

import android.service.notification.NotificationListenerService
import android.util.Log

class NotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaControls", "Notification Listener Connected")
    }
}
