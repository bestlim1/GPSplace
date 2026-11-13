package com.example.gpsplace.data.repo

import com.example.gpsplace.data.local.AppDatabase
import com.example.gpsplace.data.local.ZoneCacheEntity
import com.example.gpsplace.data.remote.Fetched
import com.example.gpsplace.data.remote.FirebaseSource
import com.example.gpsplace.data.remote.listen
import com.example.gpsplace.data.remote.millis
import com.example.gpsplace.data.remote.toZone
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.domain.ZoneType
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** 구역 (FR-04). 보호자는 Firestore에 쓰고, 보호대상자 기기는 사본을 Room에 두고 판정한다. */
class ZoneRepository(
    private val firebase: FirebaseSource,
    private val db: AppDatabase,
) {
    fun observeZones(linkId: String): Flow<Fetched<List<Zone>>> =
        firebase.zones(linkId).listen().map { snapshot ->
            Fetched(snapshot.documents.mapNotNull { it.toZone() }.sortedBy { it.name }, snapshot.metadata.isFromCache)
        }

    suspend fun zone(linkId: String, zoneId: String): Zone? =
        firebase.zones(linkId).document(zoneId).get().await().toZone()

    /**
     * 구역 저장. 오프라인이면 Firestore가 기기에 쌓아 두었다가 연결되면 보낸다.
     * 서버 확인은 10초까지만 기다린다 (그 뒤에도 저장은 진행된다).
     */
    suspend fun save(linkId: String, zoneId: String?, name: String, type: ZoneType, lat: Double, lng: Double, radiusM: Double) {
        val ref = if (zoneId == null) firebase.zones(linkId).document() else firebase.zones(linkId).document(zoneId)
        val task = ref.set(
            mapOf(
                "name" to name,
                "type" to type.key,
                "lat" to lat,
                "lng" to lng,
                "radiusM" to radiusM,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
        withTimeoutOrNull(10_000) { task.await() }
    }

    suspend fun delete(linkId: String, zoneId: String) {
        val task = firebase.zones(linkId).document(zoneId).delete()
        withTimeoutOrNull(10_000) { task.await() }
    }

    /**
     * 보호대상자 기기: 받은 구역 목록으로 사본을 맞춘다. 판정 상태는 유지하고(수정 직후부터 새 반경 적용),
     * 목록에서 빠진(삭제된) 구역은 사본에서 지운다.
     */
    suspend fun cacheZones(snapshot: QuerySnapshot) {
        val existing = db.zoneDao().all().associateBy { it.zoneId }
        val rows = snapshot.documents.mapNotNull { doc ->
            val zone = doc.toZone() ?: return@mapNotNull null
            val old = existing[zone.id]
            ZoneCacheEntity(
                zoneId = zone.id,
                name = zone.name,
                type = zone.type.key,
                lat = zone.lat,
                lng = zone.lng,
                radiusM = zone.radiusM,
                updatedAt = doc.getTimestamp("updatedAt").millis() ?: 0L,
                confirmedInside = old?.confirmedInside,
                lastInside = old?.lastInside,
                lastLat = old?.lastLat,
                lastLng = old?.lastLng,
                lastT = old?.lastT,
            )
        }
        if (rows.isEmpty()) db.zoneDao().clear() else db.zoneDao().deleteOthers(rows.map { it.zoneId })
        if (rows.isNotEmpty()) db.zoneDao().upsert(rows)
    }
}
