package com.example.gpsplace.notify

import com.example.gpsplace.GPSplaceApp
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.domain.AlertType
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** 보호자 기기: Cloud Function(onAlertCreated)이 보낸 구역 알림을 받아 휴대폰 알림으로 띄운다. */
class FcmService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val container = (application as GPSplaceApp).container
        scope.launch { runCatching { container.links.updateFcmToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val alertId = data["alertId"] ?: return
        val container = (application as GPSplaceApp).container
        val role = runBlocking { container.settings.currentLink().role }
        if (role != Role.Guardian) return
        NotificationChannels.showZoneAlert(
            context = this,
            alertId = alertId,
            type = AlertType.fromKey(data["type"]),
            zoneName = data["zoneName"].orEmpty(),
            t = data["t"]?.toLongOrNull() ?: System.currentTimeMillis(),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
