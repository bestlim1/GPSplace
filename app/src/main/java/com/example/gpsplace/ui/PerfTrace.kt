package com.example.gpsplace.ui

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * NFR-02 응답 시간 측정 (디버그 빌드에서만 logcat “NFR02”에 남긴다).
 * 화면이 처음 그려진 시점부터 [ready]가 처음 true가 될 때까지.
 * 측정: adb logcat -s NFR02 → 같은 조건에서 10회, 9회 이상 목표(홈 2초·경로 3초·목록 1초) 충족인지 기록.
 */
@Composable
fun TraceUntilReady(name: String, ready: Boolean) {
    val debuggable = (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) return
    val start = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(ready) {
        if (ready) Log.i("NFR02", "$name ${SystemClock.elapsedRealtime() - start}ms")
    }
}
