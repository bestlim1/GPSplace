package com.example.gpsplace

import android.content.Context
import com.example.gpsplace.data.NetworkMonitor
import com.example.gpsplace.data.local.AppDatabase
import com.example.gpsplace.data.local.SettingsStore
import com.example.gpsplace.data.remote.FirebaseSource
import com.example.gpsplace.data.repo.LinkRepository
import com.example.gpsplace.data.repo.LocationRepository
import com.example.gpsplace.data.repo.SyncRepository
import com.example.gpsplace.data.repo.TrackRepository
import com.example.gpsplace.data.repo.ZoneRepository
import com.example.gpsplace.location.RecordingController
import com.example.gpsplace.sync.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 수동 DI (Hilt 없이). 앱 전체에 하나. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 화면과 상관없이 끝까지 해야 하는 일 (역할 저장 등) */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock: () -> Long = System::currentTimeMillis
    val settings = SettingsStore(appContext)
    val database = AppDatabase.create(appContext)
    val firebase = FirebaseSource()
    val network = NetworkMonitor(appContext)
    val recording = RecordingController(appContext, settings)

    val links = LinkRepository(firebase, settings, clock)
    val zones = ZoneRepository(firebase, database)
    val tracks = TrackRepository(firebase)
    val sync = SyncRepository(appContext, firebase, database, settings, clock)
    val location = LocationRepository(database, settings, onRecorded = { UploadWorker.enqueue(appContext) })
}
