package com.example.gpsplace.data.repo

import android.database.sqlite.SQLiteFullException
import com.example.gpsplace.data.local.AlertEntity
import com.example.gpsplace.data.local.AppDatabase
import com.example.gpsplace.data.local.PointEntity
import com.example.gpsplace.data.local.SettingsStore
import com.example.gpsplace.data.local.StayEntity
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.domain.StayDetector
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.domain.ZoneDetector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RecordResult { Saved, Duplicate, StorageFull }

/**
 * 보호대상자 기기에서 위치 한 점을 기록한다 (FR-02, FR-06).
 * 수집 즉시 로컬에 저장하고, 체류(FR-03)와 구역 판정(FR-04)을 이 기기에서 한다 — 오프라인에서도 동작 (NFR-03).
 */
class LocationRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val onRecorded: () -> Unit,
) {
    private val mutex = Mutex()

    suspend fun record(lat: Double, lng: Double, accuracy: Float, t: Long): RecordResult = mutex.withLock {
        val inserted = try {
            db.pointDao().insert(
                PointEntity(
                    lat = lat,
                    lng = lng,
                    accuracy = accuracy,
                    t = t,
                    dayKey = SeoulTime.dayKey(t),
                    hourKey = SeoulTime.hourKey(t),
                )
            )
        } catch (e: SQLiteFullException) {
            settings.setStorageFull(true)
            return RecordResult.StorageFull
        }
        if (inserted == -1L) return RecordResult.Duplicate

        val point = TrackPoint(lat, lng, t, accuracy)
        try {
            updateStays(t)
            updateZones(point)
        } catch (e: SQLiteFullException) {
            settings.setStorageFull(true)
            return RecordResult.StorageFull
        }
        onRecorded()
        RecordResult.Saved
    }

    /** 어제 0시부터의 점으로 체류를 다시 판정하고, 새로 생기거나 종료 시각이 바뀐 체류만 저장한다. */
    private suspend fun updateStays(now: Long) {
        val since = SeoulTime.startOfDay(SeoulTime.today(now).minusDays(1))
        val points = db.pointDao().pointsSince(since).map { it.toTrackPoint() }
        val stays = StayDetector.detect(points)
        if (stays.isEmpty()) return
        val existing = db.stayDao().byIds(stays.map { it.id }).associateBy { it.id }
        val changed = stays.filter { stay ->
            val old = existing[stay.id]
            old == null || old.endAt != stay.endAt || old.lat != stay.lat || old.lng != stay.lng
        }.map { StayEntity.from(it, SeoulTime.dayKey(it.startAt)) }
        if (changed.isNotEmpty()) db.stayDao().upsert(changed)
    }

    private suspend fun updateZones(point: TrackPoint) {
        val cached = db.zoneDao().all()
        if (cached.isEmpty()) return
        val (states, alerts) = ZoneDetector.stepAll(
            zones = cached.map { it.toZone() },
            states = cached.associate { it.zoneId to it.toState() },
            point = point,
        )
        db.zoneDao().upsert(cached.map { row -> states[row.zoneId]?.let { row.withState(it) } ?: row })
        if (alerts.isNotEmpty()) db.alertDao().insert(alerts.map { AlertEntity.from(it, createdAt = point.t) })
    }
}
