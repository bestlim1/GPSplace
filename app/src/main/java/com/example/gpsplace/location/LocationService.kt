package com.example.gpsplace.location

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.gpsplace.AppContainer
import com.example.gpsplace.GPSplaceApp
import com.example.gpsplace.data.remote.listen
import com.example.gpsplace.data.repo.LinkState
import com.example.gpsplace.data.repo.RecordResult
import com.example.gpsplace.notify.NotificationChannels
import com.example.gpsplace.sync.UploadWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 보호대상자 위치 기록 포그라운드 서비스 (foregroundServiceType="location").
 * 30초마다 위치를 요청해 즉시 Room에 저장한다. 화면을 끄거나 최근 앱에서 지워도 진행 알림과 함께 계속된다.
 * 연결이 해제되면 스스로 멈추고, 보호자가 바꾼 구역을 받아 이 기기의 판정 사본을 맞춘다.
 */
class LocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var container: AppContainer
    private lateinit var fused: FusedLocationProviderClient
    private var updatesRequested = false
    private var watchJob: Job? = null
    private var receiverRegistered = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val locations = result.locations.toList()
            scope.launch {
                for (location in locations) {
                    val outcome = container.location.record(location.latitude, location.longitude, location.accuracy, location.time)
                    if (outcome == RecordResult.StorageFull) {
                        stopSelf()
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * 위치(GPS)를 켜고 끌 때 상태를 다시 읽고 보호자에게 보고한다.
     * 비행기 모드 전환처럼 시스템이 바쁠 때 메인 스레드에서 시스템 서비스를 부르면 ANR이 날 수 있어 백그라운드에서 한다.
     */
    private val providersReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val appContext = context.applicationContext
            scope.launch {
                container.recording.refreshPermissions()
                UploadWorker.enqueue(appContext)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as GPSplaceApp).container
        fused = LocationServices.getFusedLocationProviderClient(this)
        NotificationChannels.ensure(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startInForeground() || !PermissionState.hasLocation(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        container.recording.setServiceRunning(true)
        if (!updatesRequested) requestUpdates()
        if (watchJob == null) watchJob = scope.launch { watchLinkAndZones() }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this, providersReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        UploadWorker.enqueue(this)
        return START_STICKY
    }

    private fun startInForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NotificationChannels.RECORDING_NOTIFICATION_ID,
            NotificationChannels.recording(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (e: Exception) {
        // 위치 권한이 없거나 백그라운드에서 시작이 막힘
        false
    }

    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            updatesRequested = true
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private suspend fun watchLinkAndZones() {
        val linkId = container.settings.currentLink().linkId ?: return
        runCatching { container.firebase.ensureSignedIn() }
        scope.launch {
            container.firebase.zones(linkId).listen()
                .onEach { snapshot ->
                    // 오프라인 첫 응답이 비어 있으면 기기 사본을 지우지 않는다
                    if (!snapshot.metadata.isFromCache || !snapshot.isEmpty) container.zones.cacheZones(snapshot)
                }
                .catch { }
                .collect()
        }
        container.links.observeLink(linkId).collect { state ->
            if (state is LinkState.Released) {
                container.links.onReleased()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        if (updatesRequested) fused.removeLocationUpdates(callback)
        if (receiverRegistered) unregisterReceiver(providersReceiver)
        container.recording.setServiceRunning(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** NFR-01 위치 수집 요청 주기 30초 */
        const val INTERVAL_MS = 30_000L
    }
}
