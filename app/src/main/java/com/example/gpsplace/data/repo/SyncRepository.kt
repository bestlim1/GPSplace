package com.example.gpsplace.data.repo

import android.content.Context
import com.example.gpsplace.data.local.AppDatabase
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.local.SettingsStore
import com.example.gpsplace.data.remote.FirebaseSource
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.location.PermissionState
import com.example.gpsplace.data.remote.toMap
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date

enum class UploadOutcome { Done, Retry, Stopped }

/** 전송 진행 (S-17 “전송 중 12 / 23”) */
data class UploadProgress(val sent: Int, val total: Int)

/**
 * 미전송 기록을 서버로 보낸다 (FR-06).
 * - 점은 hourKey별로 묶어 pointHours 시간 문서에 merge한다. 맵 키가 수집 시각("p{t}")이라 다시 보내도 늘지 않는다.
 * - 한 번에 오래된 점부터 보내되, 가장 최근 점은 항상 같이 보내 보호자가 현재 위치를 먼저 보게 한다.
 * - 보낼 때마다 남은 미전송 건수를 links.pendingCount에, 권한·위치 서비스 상태를 links.permission에 남긴다.
 */
class SyncRepository(
    private val context: Context,
    private val firebase: FirebaseSource,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _progress = MutableStateFlow<UploadProgress?>(null)
    val progress: StateFlow<UploadProgress?> = _progress.asStateFlow()

    suspend fun uploadAll(): UploadOutcome {
        val link = settings.currentLink()
        val linkId = link.linkId
        if (link.role != Role.Protected || !link.linkActive || linkId == null) return UploadOutcome.Stopped
        return try {
            withTimeout(SIGN_IN_TIMEOUT_MS) { firebase.ensureSignedIn() }
            val total = db.pointDao().unsyncedCount()
            var sent = 0
            _progress.value = UploadProgress(0, total)
            var round = 0
            while (round++ < MAX_ROUNDS) {
                val count = uploadBatch(linkId)
                sent += count
                _progress.value = UploadProgress(sent.coerceAtMost(total), total)
                if (count == 0) break
            }
            settings.setUploadResult(clock(), null)
            UploadOutcome.Done
        } catch (e: FirebaseFirestoreException) {
            settings.setUploadResult(null, e.code.name)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) UploadOutcome.Stopped else UploadOutcome.Retry
        } catch (e: Exception) {
            settings.setUploadResult(null, e.javaClass.simpleName)
            UploadOutcome.Retry
        } finally {
            _progress.value = null
        }
    }

    /** 한 번의 일괄 쓰기. 보낸 점 수를 돌려준다 (점·체류·알림이 없으면 상태만 보고하고 0). */
    private suspend fun uploadBatch(linkId: String): Int {
        val pointDao = db.pointDao()
        val latest = pointDao.latest()
        val oldest = pointDao.oldestUnsynced(POINTS_PER_BATCH)
        val newest = pointDao.newestUnsynced()
        val points = (oldest + listOfNotNull(newest)).distinctBy { it.id }
        val stays = db.stayDao().unsynced().take(DOCS_PER_BATCH)
        val alerts = db.alertDao().unsynced().take(DOCS_PER_BATCH)

        val batch = firebase.db.batch()
        points.groupBy { it.hourKey }.forEach { (hourKey, hourPoints) ->
            val first = hourPoints.first()
            batch.set(
                firebase.pointHours(linkId).document(hourKey),
                mapOf(
                    "dayKey" to first.dayKey,
                    "hour" to SeoulTime.hour(first.t),
                    "startAt" to SeoulTime.hourStart(first.t),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "points" to hourPoints.associate { "p${it.t}" to it.toTrackPoint().toMap(withAccuracy = true) },
                ),
                SetOptions.merge(),
            )
        }
        stays.forEach { stay ->
            batch.set(
                firebase.stays(linkId).document(stay.id),
                mapOf(
                    "startAt" to stay.startAt,
                    "endAt" to stay.endAt,
                    "lat" to stay.lat,
                    "lng" to stay.lng,
                    "dayKey" to stay.dayKey,
                    "durationSec" to stay.durationSec,
                ),
            )
        }
        alerts.forEach { alert ->
            batch.set(
                firebase.alerts(linkId).document(alert.id),
                mapOf(
                    "zoneId" to alert.zoneId,
                    "zoneName" to alert.zoneName,
                    "type" to alert.type,
                    "p1" to TrackPoint(alert.p1Lat, alert.p1Lng, alert.p1T).toMap(),
                    "p2" to TrackPoint(alert.p2Lat, alert.p2Lng, alert.p2T).toMap(),
                    "zone" to mapOf("lat" to alert.zoneLat, "lng" to alert.zoneLng, "radiusM" to alert.radiusM),
                    "createdAt" to Timestamp(Date(alert.createdAt)),
                ),
            )
        }
        val permissions = PermissionState.read(context)
        val linkUpdate = buildMap<String, Any> {
            put("pendingCount", (pointDao.unsyncedCount() - points.size).coerceAtLeast(0))
            put("permission", LinkRepository.permissionMap(permissions.location, permissions.locationServiceOn))
            put("reportedAt", FieldValue.serverTimestamp())
            if (latest != null) put("lastPoint", latest.toTrackPoint().toMap())
        }
        batch.update(firebase.link(linkId), linkUpdate)

        withTimeout(COMMIT_TIMEOUT_MS) { batch.commit().await() }

        val now = clock()
        if (points.isNotEmpty()) pointDao.markSynced(points.map { it.id }, now)
        stays.forEach { db.stayDao().markSynced(it.id, it.endAt, now) }
        if (alerts.isNotEmpty()) db.alertDao().markSynced(alerts.map { it.id }, now)
        return points.size + stays.size + alerts.size
    }

    companion object {
        private const val POINTS_PER_BATCH = 300
        private const val DOCS_PER_BATCH = 80
        private const val MAX_ROUNDS = 50
        private const val COMMIT_TIMEOUT_MS = 10_000L
        private const val SIGN_IN_TIMEOUT_MS = 10_000L
    }
}
