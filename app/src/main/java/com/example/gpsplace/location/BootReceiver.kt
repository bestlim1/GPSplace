package com.example.gpsplace.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.gpsplace.GPSplaceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 휴대폰을 다시 켜거나 앱이 업데이트된 뒤 위치 기록을 이어 간다 (FR-02 “화면을 꺼도 기록이 이어진다”).
 * 연결·권한 조건은 RecordingController가 다시 확인한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val container = (context.applicationContext as GPSplaceApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.recording.sync()
            } finally {
                pending.finish()
            }
        }
    }
}
