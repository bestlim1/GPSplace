package com.example.gpsplace.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** 기기의 권한·위치 서비스 상태. 화면 진입·설정에서 복귀할 때마다 다시 읽는다. */
data class PermissionState(
    /** 위치 권한 (정확한 위치 또는 대략적 위치) */
    val location: Boolean,
    /** 위치 권한 ‘항상 허용’ */
    val background: Boolean,
    /** 휴대폰 알림 */
    val notifications: Boolean,
    /** 휴대폰 위치(GPS) 켜짐 */
    val locationServiceOn: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionState(
            location = hasLocation(context),
            background = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            notifications = notificationsEnabled(context),
            locationServiceOn = locationServiceOn(context),
        )

        fun hasLocation(context: Context): Boolean =
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        fun notificationsEnabled(context: Context): Boolean {
            val runtime = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                granted(context, Manifest.permission.POST_NOTIFICATIONS)
            return runtime && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        fun locationServiceOn(context: Context): Boolean =
            LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
