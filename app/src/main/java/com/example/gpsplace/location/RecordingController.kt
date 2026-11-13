package com.example.gpsplace.location

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.local.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 보호대상자 위치 기록을 켜고 끈다.
 * 연결이 살아 있고 위치 권한이 있고 저장 공간이 남아 있을 때만 기록한다 (NFR-05, UC-01 A2).
 */
class RecordingController(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val _permissions = MutableStateFlow(PermissionState.read(context))
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun refreshPermissions() {
        _permissions.value = PermissionState.read(context)
    }

    internal fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    suspend fun shouldRecord(): Boolean {
        val link = settings.currentLink()
        return link.role == Role.Protected && link.linkActive && link.linkId != null &&
            PermissionState.hasLocation(context) && !settings.storageFull.first()
    }

    /** 지금 상태에 맞게 기록 서비스를 시작하거나 멈춘다. 시스템 서비스 호출이 있어 메인 스레드 밖에서 한다. */
    suspend fun sync() = withContext(Dispatchers.Default) {
        refreshPermissions()
        if (shouldRecord()) start() else stop()
    }

    private fun start() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
        } catch (e: Exception) {
            // 백그라운드에서 포그라운드 서비스 시작이 막힌 경우: 다음에 앱을 열 때 다시 시작한다.
            Log.w(TAG, "기록 서비스를 시작하지 못했습니다", e)
        }
    }

    fun stop() {
        context.stopService(Intent(context, LocationService::class.java))
    }

    companion object {
        private const val TAG = "RecordingController"
    }
}
