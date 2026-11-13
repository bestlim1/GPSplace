package com.example.gpsplace.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.gpsplace.MainActivity
import com.example.gpsplace.R
import com.example.gpsplace.domain.AlertType
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.location.PermissionState

object NotificationChannels {
    const val RECORDING = "recording"
    const val ZONE_ALERTS = "zone_alerts"
    const val RECORDING_NOTIFICATION_ID = 1

    const val EXTRA_OPEN = "open"
    const val OPEN_RECORDING = "recording"
    const val OPEN_ALERT = "alert"
    const val EXTRA_ALERT_ID = "alertId"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(RECORDING, context.getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(ZONE_ALERTS, context.getString(R.string.channel_zone_alerts), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** 위치 기록 진행 알림 “위치 기록 중” → 누르면 S-16 */
    fun recording(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_RECORDING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, RECORDING)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(context.getString(R.string.notification_recording))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** 구역 알림 (보호자 기기) → 누르면 S-09, 백스택 S-03 → S-08 → S-09 */
    fun showZoneAlert(context: Context, alertId: String, type: AlertType, zoneName: String, t: Long) {
        if (!PermissionState.notificationsEnabled(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_ALERT)
            .putExtra(EXTRA_ALERT_ID, alertId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, alertId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(if (type == AlertType.SafeExit) R.string.alert_safe_exit else R.string.alert_caution_enter)
        val notification = NotificationCompat.Builder(context, ZONE_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_zone)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alert_row_detail, zoneName, SeoulTime.hm(t)))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(alertId.hashCode(), notification)
        } catch (e: SecurityException) {
            // 알림 권한이 꺼져 있으면 울리지 않는다. 목록(S-08)에는 그대로 남는다 (NFR-05).
        }
    }
}
