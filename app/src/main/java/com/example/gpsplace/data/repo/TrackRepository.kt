package com.example.gpsplace.data.repo

import com.example.gpsplace.data.remote.Fetched
import com.example.gpsplace.data.remote.FirebaseSource
import com.example.gpsplace.data.remote.RemoteAlert
import com.example.gpsplace.data.remote.hourPoints
import com.example.gpsplace.data.remote.listen
import com.example.gpsplace.data.remote.toRemoteAlert
import com.example.gpsplace.data.remote.toStay
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.domain.TrackPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * 보호자가 보는 경로·체류·알림 (FR-02, FR-03, FR-05).
 * 읽기 아끼기: 오늘은 스냅샷 리스너로 바뀐 시간 문서만 받고, 지난 날짜는 한 번 읽으면 이 실행 동안 다시 쓴다.
 */
class TrackRepository(private val firebase: FirebaseSource) {
    private val pastDays = ConcurrentHashMap<String, List<TrackPoint>>()

    /** 하루치 점. pointHours.where(dayKey == X) → 시간 문서 최대 24건 */
    fun observeDayPoints(linkId: String, dayKey: String, live: Boolean): Flow<Fetched<List<TrackPoint>>> {
        val query = firebase.pointHours(linkId).whereEqualTo("dayKey", dayKey)
        if (live) {
            return query.listen().map { snapshot ->
                Fetched(snapshot.documents.flatMap { it.hourPoints() }.sortedBy { it.t }, snapshot.metadata.isFromCache)
            }
        }
        val key = "$linkId/$dayKey"
        return flow {
            pastDays[key]?.let { emit(Fetched(it, fromCache = false)); return@flow }
            val snapshot = query.get().await()
            val points = snapshot.documents.flatMap { it.hourPoints() }.sortedBy { it.t }
            if (!snapshot.metadata.isFromCache) pastDays[key] = points
            emit(Fetched(points, snapshot.metadata.isFromCache))
        }
    }

    /** 하루치 체류. 시작 시각 순 (번호 1 = 그날 첫 체류) */
    fun observeDayStays(linkId: String, dayKey: String): Flow<Fetched<List<Stay>>> =
        firebase.stays(linkId).whereEqualTo("dayKey", dayKey).listen().map { snapshot ->
            Fetched(snapshot.documents.mapNotNull { it.toStay() }.sortedBy { it.startAt }, snapshot.metadata.isFromCache)
        }

    /** 최근 알림, 최신이 먼저 */
    fun observeAlerts(linkId: String): Flow<Fetched<List<RemoteAlert>>> =
        firebase.alerts(linkId).orderBy("createdAt", Query.Direction.DESCENDING).limit(ALERT_LIMIT).listen().map { snapshot ->
            Fetched(snapshot.documents.mapNotNull { it.toRemoteAlert() }, snapshot.metadata.isFromCache)
        }

    suspend fun alert(linkId: String, alertId: String): RemoteAlert? =
        firebase.alerts(linkId).document(alertId).get().await().toRemoteAlert()

    companion object {
        private const val ALERT_LIMIT = 200L
    }
}
